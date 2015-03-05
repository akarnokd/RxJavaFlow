/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.operators;

import static rx.internal.UnsafeAccess.*;

import java.util.function.*;

import rx.*;
import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.disposables.Disposable;
import rx.exceptions.Exceptions;
import rx.internal.*;
import rx.internal.queues.SpscArrayQueue;
import rx.observables.ConnectableObservable;
import rx.subscribers.*;

/**
 * TODO javadoc
 * 
 * @param <T>
 */
public final class OperatorPublish<T> extends ConnectableObservable<T> {
    
    /** The current connection. */
    static final class PublishState<T> {
        /** The current state, never null. */
        PublishSubscriber<T> current = new PublishSubscriber<>();
    }
    
    /**
     * Creates a ConnectableObservable that shares a single underlying connection
     * to a source.
     * @param source
     * @return
     */
    public static <T> ConnectableObservable<T> create(Observable<? extends T> source) {
        PublishState<T> ps = new PublishState<>();
        return new OperatorPublish<>(source, ps);
    }

    /**
     * Creates a Observable which shares a single underlying connection to
     * the source through a Observable transformation returned by the selector.
     * @param source
     * @param selector
     * @return
     */
    public static <T, R> Observable<R> create(final Observable<? extends T> source, 
            final Function<? super Observable<T>, ? extends Observable<R>> selector) {
        return Observable.create(child -> {
            DisposableSubscriber<R> ds = new DefaultDisposableSubscriber<>(child);
            PublishState<T> ps = new PublishState<>();
            OperatorPublish<T> op = new OperatorPublish<>(source, ps);
            
            selector.apply(op).unsafeSubscribe(ds);
            
            op.connect(sub -> ds.add(sub));
        });
    }

    final Observable<? extends T> source;

    final PublishState<T> state;

    private OperatorPublish(Observable<? extends T> source, PublishState<T> state) {
        super(s -> { 
            PublishSubscriber<T> c = state.current;
            InnerSubscription<T> inner = new InnerSubscription<>(s, c);
            inner.setRemover();
            
            s.onSubscribe(inner);
            
            if (!c.psm.add(inner)) {
                Object term = c.psm.terminal;
                NotificationLite<T> nl = NotificationLite.instance();
                if (nl.isCompleted(term)) {
                    inner.complete();
                } else {
                    inner.errorFinal(nl.getError(term));
                }
            } else {
                c.dispatch();
            }
        });
        this.source = source;
        this.state = state;
    }

    @Override
    public void connect(Consumer<? super Disposable> connection) {
        PublishSubscriber<T> c;
        boolean connect = false;
        synchronized (this) {
            c = state.current;
            if (c.isDisposed()) {
                c = new PublishSubscriber<>();
                state.current = c;
            }
            // only one should try to connect
            if (!c.connecting) {
                connect = true;
                c.connecting = true;
            }
        }
        connection.accept(c);
        if (connect) {
            source.unsafeSubscribe(c);
        }
    }
    
    /**
     * Returns true if there are subscribers
     * @return
     */
    public boolean hasSubscribers() {
        return state.current.psm.array().length > 0;
    }
    
    /**
     * Returns true if there is a connection started but not yet connected.
     * @return
     */
    public boolean isConnecting() {
        PublishSubscriber<T> c = state.current;
        return c.connecting && !c.connected && !c.done && !c.isDisposed();
    }
    /**
     * Returns true if there is an active connection.
     * @return
     */
    public boolean isConnected() {
        PublishSubscriber<T> c = state.current;
        return c.connected && !c.done && !c.isDisposed();
    }
    
    /**
     * Retunrs true if the source has completed and/or disconnected.
     * @return
     */
    public boolean isDisposed() {
        PublishSubscriber<T> c = state.current;
        return c.isDisposed();
    }
    
    /**
     * Manages an array of inner subscriptions for a PublishSubscriber.
     *
     * @param <T> the published value type
     */
    static final class PublishSubscriptionManager<T> extends AbstractArrayManager<InnerSubscription<T>> {
        @SuppressWarnings("unchecked")
        public PublishSubscriptionManager() {
            super(i -> new InnerSubscription[i]);
        }
        /** The terminal value if not null. */
        volatile Object terminal;
        static final long TERMINAL = addressOf(PublishSubscriptionManager.class, "terminal");
        
        void soTerminal(Object value) {
            UNSAFE.putOrderedObject(this, TERMINAL, value);
        }
    }
    
    static final class PublishSubscriber<T> extends AbstractSubscriber<T> implements Disposable {
        final NotificationLite<T> nl;
        final SpscArrayQueue<T> queue;
        final PublishSubscriptionManager<T> psm;

        /** Guarded by the parent OperatorPublish, set to true on the first attempt to connect an unconnected subscriber. */
        volatile boolean connecting;
        /** True if subscriber has been received. */
        volatile boolean connected;
        /** True if the source has terminated. */
        volatile boolean done;
        
        /** Guarded by this. */
        boolean emitting;
        /** Guarded by this. */
        boolean missed;
        
        /** Holds a disposable wrapper of the subscription. */
        volatile Disposable disconnect;
        static final long DISCONNECT = addressOf(PublishSubscriber.class, "disconnect");
        
        public PublishSubscriber() {
            this.nl = NotificationLite.instance();
            this.queue = new SpscArrayQueue<>(Flow.defaultBufferSize());
            this.psm = new PublishSubscriptionManager<>();
        }
        
        @Override
        public void dispose() {
            TerminalAtomics.dispose(this, DISCONNECT);
        }
        
        @Override
        public boolean isDisposed() {
            return TerminalAtomics.isDisposed(this, DISCONNECT);
        }
        
        @Override
        protected void onSubscribe() {
            // TODO our subscriptions are threadsafe by design
            Subscription s = subscription;
            Disposable d = Disposable.from(s);
            
            if (!TerminalAtomics.set(this, DISCONNECT, d)) {
                done = true;
                return;
            }
            
            connected = true;
            s.request(Flow.defaultBufferSize());
        }
        @Override
        public void onNext(T item) {
            Conformance.itemNonNull(item);
            Conformance.subscriptionNonNull(subscription);
            
            if (!queue.offer(item)) {
                psm.soTerminal(nl.error(Conformance.mustRequestFirst()));
                done = true;
            }
            dispatch();
        }
        @Override
        public void onError(Throwable throwable) {
            Conformance.throwableNonNull(throwable);
            Conformance.subscriptionNonNull(subscription);
            
            if (done) {
                return;
            }
            psm.soTerminal(nl.error(throwable));
            done = true;
            dispose();
            dispatch();
        }
        @Override
        public void onComplete() {
            Conformance.subscriptionNonNull(subscription);
            
            if (done) {
                return;
            }
            psm.soTerminal(nl.complete());
            done = true;
            dispose();
            dispatch();
        }
        
        void dispatch() {
            synchronized (this) {
                if (emitting) {
                    missed = true;
                    return;
                }
                emitting = true;
                missed = false;
            }
            boolean skipFinal = false;
            try {
                for (;;) {

                    for (;;) {
                        
                        boolean empty = queue.isEmpty();
                        if (canTerminate(psm.terminal, empty)) {
                            skipFinal = true;
                            return;
                        }
                        if (!empty) {
                            InnerSubscription<T>[] array = psm.array();
                            int len = array.length;
                            
                            long maxRequested = Long.MAX_VALUE;
                            int ignore = 0;
                            for (InnerSubscription<T> is : array) {
                                long ir = is.requested;
                                // ignore NO_REQUEST and CANCEL
                                if (ir == 0) {
                                    // found somebody not ready
                                    maxRequested = 0;
                                    break;
                                } else
                                if (ir > 0) {
                                    maxRequested = Math.min(maxRequested, ir);
                                } else {
                                    // either cancelled or not yet requested
                                    ignore++;
                                }
                            }
                            
                            if (ignore == len) {
                                // no one is interested yet/anymore, drop the value
                                queue.poll();
                                // eagerly check terminal state
                                if (canTerminate(psm.terminal, empty)) {
                                    skipFinal = true;
                                    return;
                                }
                                // request a replacement
                                subscription.request(1);
                            } else {
                                // there are interested parties,
                                long d = 0;
                                while (d < maxRequested) {
                                    T v = queue.poll();
                                    empty = v == null;
                                    if (canTerminate(psm.terminal, empty)) {
                                        skipFinal = true;
                                        return;
                                    }
                                    if (empty) {
                                        break;
                                    }
                                    for (InnerSubscription<T> is : array) {
                                        long ir = is.requested;
                                        if (ir > 0) {
                                            try {
                                                is.subscriber.onNext(v);
                                            } catch (Throwable t) {
                                                is.error(t);
                                                continue;
                                            }
                                            is.produced(1); // might be quite inefficient, but required by eager
                                        }
                                    }
                                    d++;
                                }
                                // replenish from the source
                                if (d > 0) {
                                    subscription.request(d);
                                }
                                if (maxRequested == 0 || empty) {
                                    break;
                                }
                           }
                        } else {
                            break;
                        }
                    }
                    
                    synchronized (this) {
                        if (!missed) {
                            skipFinal = true;
                            emitting = false;
                            return;
                        }
                        missed = false;
                    }
                }
            } finally {
                if (!skipFinal) {
                    synchronized (this) {
                        emitting = false;
                    }
                }
            }
        }
        
        /**
         * Check the terminal state and emptyness, terminate
         * the psm if necessary and deliver the terminal even
         * @param term
         * @param empty
         * @return
         */
        boolean canTerminate(Object term, boolean empty) {
            if (term != null) {
                if (nl.isCompleted(term)) {
                    if (empty) {
                        for (InnerSubscription<T> s : psm.getAndTerminate()) {
                            s.complete();
                        }
                        return true;
                    }
                } else {
                    queue.clear();
                    Throwable t = nl.getError(term);
                    for (InnerSubscription<T> s : psm.getAndTerminate()) {
                        s.errorFinal(t);
                    }
                    return true;
                }
            }
            return false;
        }
    }
    
    /**
     * Manages the requests from the downstream subscriber and
     * helps dispatching errors and completion.
     * 
     * @param <T> the value type emitted
     */
    static final class InnerSubscription<T> implements Subscription {
        /** The current requested count, negative value indicates cancelled subscription. */
        volatile long requested;
        static final long REQUESTED = addressOf(InnerSubscription.class, "requested");

        final Subscriber<? super T> subscriber;
        final PublishSubscriber<T> parent;

        volatile Disposable remove;
        static final long REMOVE = addressOf(InnerSubscription.class, "remove");
        
        public InnerSubscription(Subscriber<? super T> subscriber, PublishSubscriber<T> parent) {
            this.subscriber = Conformance.subscriberNonNull(subscriber);
            this.parent = parent;
            UNSAFE.putOrderedLong(this, REQUESTED, TerminalAtomics.NO_REQUEST);
        }

        /**
         * Assigns a remove action to this Subscription, must be called
         * before the subscription is set on a Subscriber.
         */
        public void setRemover() {
            Disposable d = Disposable.from(() -> parent.psm.remove(this));
            UNSAFE.putOrderedObject(this, REMOVE, d);
        }
        
        @Override
        public void request(long n) {
            if (!Conformance.requestPositive(n, subscriber)) {
                cancel();
                return;
            }
            TerminalAtomics.requestUnrequested(this, REQUESTED, n);
            parent.dispatch();
        }
        
        public void produced(long n) {
            TerminalAtomics.producedChecked(this, REQUESTED, n, subscriber, this);
        }
        
        @Override
        public void cancel() {
            if (TerminalAtomics.cancel(this, REQUESTED)) {
                TerminalAtomics.dispose(this, REMOVE);
            }
        }
        
        /** 
         * Called from the dispatcher loop when it encounters a terminal state
         * where there is no need to remove the Disposable from the psm.
         */
        public void terminate() {
            TerminalAtomics.cancel(this, REQUESTED);
            UNSAFE.putOrderedObject(this, REMOVE, null);
        }
        
        /**
         * Called if the onNext threw.
         */
        void error(Throwable t) {
            t = Conformance.onNextThrew(t);
            try {
                try {
                    subscriber.onError(t);
                } catch (Throwable e) {
                    Exceptions.handleUncaught(t);
                    Exceptions.handleUncaught(Conformance.onErrorThrew(e));
                }
            } finally {
                try {
                    cancel();
                } catch (Throwable t3) {
                    Exceptions.handleUncaught(Conformance.cancelThrew(t3));
                }
            }
        }
        /**
         * Called if the source has error'd.
         */
        void errorFinal(Throwable t) {
            try {
                try {
                    subscriber.onError(t);
                } catch (Throwable e) {
                    Exceptions.handleUncaught(t);
                    Exceptions.handleUncaught(Conformance.onErrorThrew(e));
                }
            } finally {
                // no need to remove ourselves from psm.
                terminate();
            }
        }
        /**
         * Called if the source has completed.
         */
        void complete() {
            try {
                try {
                    subscriber.onComplete();
                } catch (Throwable e) {
                    Exceptions.handleUncaught(Conformance.onCompleteThrew(e));
                }
            } finally {
                // no need to remove ourselves from psm.
                terminate();
            }
        }
    }
}
