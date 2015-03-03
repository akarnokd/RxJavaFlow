/**
 * Copyright 2015 David Karnok
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rxjf.internal.operators;

import static rxjf.internal.UnsafeAccess.*;

import java.util.*;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.Flowable.Operator;
import rxjf.disposables.*;
import rxjf.exceptions.CompositeException;
import rxjf.internal.*;
import rxjf.internal.queues.*;
import rxjf.subscribers.AbstractSubscriber;

/**
 * 
 */
public final class OperatorMerge<T> implements Operator<T, Flowable<? extends T>> {
    final boolean delayErrors;
    final int maxConcurrent;
    /** Lazy initialization via inner-class holder. */
    private static final class HolderNoDelay {
        /** A singleton instance. */
        static final OperatorMerge<Object> INSTANCE = new OperatorMerge<>(false, Integer.MAX_VALUE);
    }
    /** Lazy initialization via inner-class holder. */
    private static final class HolderDelayErrors {
        /** A singleton instance. */
        static final OperatorMerge<Object> INSTANCE = new OperatorMerge<>(true, Integer.MAX_VALUE);
    }

    /**
     * @param delayErrors should the merge delay errors?
     * @return a singleton instance of this stateless operator.
     */
    @SuppressWarnings("unchecked")
    public static <T> OperatorMerge<T> instance(boolean delayErrors) {
        if (delayErrors) {
            return (OperatorMerge<T>)HolderDelayErrors.INSTANCE;
        }
        return (OperatorMerge<T>)HolderNoDelay.INSTANCE;
    }
    
    public static <T> OperatorMerge<T> instance(boolean delayErrors, int maxConcurrent) {
        return new OperatorMerge<>(delayErrors, maxConcurrent);
    }
    
    private OperatorMerge(boolean delayErrors, int maxConcurrent) {
        this.delayErrors = delayErrors;
        this.maxConcurrent = maxConcurrent;
    }
    @Override
    public Subscriber<? super Flowable<? extends T>> apply(Subscriber<? super T> child) {
        return new MergeSubscriber<>(child, delayErrors, maxConcurrent);
    }
    /**
     * Subscriber receiving Flowable sources and manages the backpressure-aware
     * emission of buffered values.
     * 
     * @param <T> the value type to emit
     */
    static final class MergeSubscriber<T> extends AbstractSubscriber<Flowable<? extends T>> implements Subscription {
        /** Avoid allocating the empty array on drain. */
        static final Object[] EMPTY = new Object[0];
        /** Indicates if errors should be delayed until all sources have produced. */
        final boolean delayErrors;
        /** The maximum number of simultaneous subscriptions to sources. */
        final int maxConcurrent;
        /** The actual subscriber receiving the events. */
        final Subscriber<? super T> actual;
        /** Queue for errors. */
        final Queue<Throwable> errors;
        /** The composite tracking all inner subscriptions. */
        final CompositeDisposable composite;
        /** Guarded by itself. */
        final List<InnerSubscriber> subscribers;
        
        /** Indicates the main source has completed. */
        volatile boolean done;
        
        /** Guarded by this. */
        boolean emitting;
        /** Guarded by this. */
        boolean missed;
        /** The last buffer index in the round-robin drain scheme. Accessed while emitting == true. */
        int lastIndex;
        
        /** The queue holding the scalar values. */
        volatile Queue<T> scalarQueue;
        static final long SCALAR_QUEUE = addressOf(MergeSubscriber.class, "scalarQueue");
        
        /** Tracks the downstream requested amounts. */
        volatile long requested;
        static final long REQUESTED = addressOf(MergeSubscriber.class, "requested");
        
        /** Holds an unique index generated for new InnerSubscriptions. */
        volatile long index;
        static final long INDEX = addressOf(MergeSubscriber.class, "index");
        
        public MergeSubscriber(Subscriber<? super T> actual, boolean delayErrors, int maxConcurrent) {
            this.actual = actual;
            this.delayErrors = delayErrors;
            this.maxConcurrent = maxConcurrent;
            this.errors = new MpscLinkedQueue<>();
            this.composite = new CompositeDisposable();
            this.subscribers = new ArrayList<>();
        }
        @Override
        public void onSubscribe() {
            composite.add(Disposable.from(subscription));
            actual.onSubscribe(this);
            if (maxConcurrent == Integer.MAX_VALUE) {
                subscription.request(Long.MAX_VALUE);
            } else {
                subscription.request(maxConcurrent);
            }
        }
        @Override
        public void onNext(Flowable<? extends T> item) {
            Conformance.itemNonNull(item);
            Conformance.subscriptionNonNull(subscription);
            if (done || requested < 0) {
                return;
            }
            
            if (item instanceof ScalarSynchronousFlowable) {
                T scalar = ((ScalarSynchronousFlowable<? extends T>)item).get();
                getScalarQueue().offer(scalar);
                if (maxConcurrent != Integer.MAX_VALUE) {
                    subscription.request(1);
                }
            } else {
                int index = UNSAFE.getAndAddInt(this, INDEX, 1);
                InnerSubscriber inner = new InnerSubscriber(index);
                synchronized (subscribers) {
                    subscribers.add(inner);
                }
                item.unsafeSubscribe(inner);
            }
            
            drain();
        }
        
        /** Returns or creates the queue to hold scalar values. */
        Queue<T> getScalarQueue() {
            Queue<T> q = scalarQueue;
            if (q == null) {
                q = new SpscLinkedQueue<>();
                UNSAFE.putOrderedObject(this, SCALAR_QUEUE, q);
            }
            return q;
        }
        
        @Override
        public void onError(Throwable throwable) {
            Conformance.throwableNonNull(throwable);
            Conformance.subscriptionNonNull(subscription);
            errors.offer(throwable);
            done = true;
            
            drain();
        }
        @Override
        public void onComplete() {
            Conformance.subscriptionNonNull(subscription);
            done = true;
            
            drain();
        }
        
        @Override
        public void request(long n) {
            if (!Conformance.requestPositive(n, this)) {
                return;
            }
            TerminalAtomics.request(this, REQUESTED, n);
            drain();
        }
        
        @Override
        public void cancel() {
            long r = requested;
            if (r >= 0) {
                r = UNSAFE.getAndSetLong(this, REQUESTED, Long.MIN_VALUE);
                if (r >= 0) {
                    synchronized (subscribers) {
                        subscribers.clear();
                    }
                    composite.dispose();
                    // release the scalar queue
                    UNSAFE.putOrderedObject(this, SCALAR_QUEUE, null);
                }
            }
        }

        void completeInner(InnerSubscriber inner) {
            if (inner.queue.isEmpty()) {
                removeInner(inner);
            }
            
            if (maxConcurrent != Integer.MAX_VALUE) {
                subscription.request(1);
            }
            drain();
        }
        
        void removeInner(InnerSubscriber inner) {
            synchronized (subscribers) {
                subscribers.remove(inner);
            }
            composite.remove(inner.disposable);
        }
        
        void errorInner(InnerSubscriber inner, Throwable error) {
            if (!delayErrors || inner.queue.isEmpty()) {
                removeInner(inner);
            }
            errors.offer(error);
            if (maxConcurrent != Integer.MAX_VALUE && delayErrors) {
                subscription.request(1);
            }
            drain();
        }
        
        long produced(long n) {
            if (n < 0) {
                actual.onError(new IllegalArgumentException("Negative produced value: " + n));
                cancel();
                return Long.MIN_VALUE;
            }
            return TerminalAtomics.producedChecked(this, REQUESTED, n, actual, this);
        }
        
        void drain() {
            synchronized (this) {
                if (emitting) {
                    missed = true;
                    return;
                }
                emitting = true;
                missed = false;
            }
            
            final List<InnerSubscriber> list = this.subscribers;
            final Subscriber<? super T> child = this.actual;
            Object[] active = EMPTY;
            
            boolean skipFinal = false;
            
            try {
                outer:
                for (;;) {
                    long r = requested;
                    if (r < 0) {
                        skipFinal = true;
                        return;
                    }
                    if (!delayErrors && !errors.isEmpty()) {
                        skipFinal = true;
                        reportErrors(child);
                        cancel();
                        return;
                    }

                    Queue<T> sq = scalarQueue;
                    if (r > 0) {
                        if (sq != null) {
                            for (;;) {
                                T v = sq.poll();
                                if (v == null) {
                                    break;
                                }
                                
                                child.onNext(v);
                                
                                r = produced(1);
                                if (r < 0) {
                                    skipFinal = true;
                                    return;
                                } else
                                if (r == 0) {
                                    break;
                                }
                            }
                        }
                    }
                    
                    // get the list of active InnerSubscribers
                    int size;
                    synchronized (list) {
                        size = list.size();
                        if (active.length == size) {
                            if (size > 0) {
                                list.toArray(active);
                            }
                        } else {
                            active = list.toArray();
                        }
                    }
                    // see if main is done and no more active InnerSubscribers
                    if  ((size == 0 && done && (sq == null || sq.isEmpty())) 
                            || (!delayErrors && !errors.isEmpty())) {
                        skipFinal = true;
                        if (!errors.isEmpty()) {
                            reportErrors(child);
                        } else {
                            child.onComplete();
                        }
                        cancel();
                        return;
                    }
                    
                    if (r > 0 && size > 0) {
                        int idx = lastIndex;

                        // locate the inner subscriber to resume the round-robin
                        int resumeIndex = 0;
                        int j = 0;
                        for (Object o : active) {
                            @SuppressWarnings("unchecked")
                            InnerSubscriber e = (InnerSubscriber)o;
                            if (e.index == idx) {
                                resumeIndex = j;
                                break;
                            }
                            j++;
                        }
                        for (int i = 0; i < active.length; i++) {
                            j = (i + resumeIndex) % active.length;
                            
                            @SuppressWarnings("unchecked")
                            InnerSubscriber e = (InnerSubscriber)active[j];
                            Queue<T> q = e.queue;
                            lastIndex = e.index;

                            // if drain to completion, ask for the next subscriber
                            if (e.done && q.isEmpty()) {
                                removeInner(e);
                                if (maxConcurrent != Integer.MAX_VALUE) {
                                    subscription.request(1);
                                }
                                continue outer;
                            }

                            int consumed = 0;
                            T v;
                            while (r > 0 && (v = q.poll()) != null) {
                                child.onNext(v);
                                consumed++;
                                r = produced(1);
                            }
                            if (r < 0) {
                                skipFinal = true;
                                return;
                            }
                            if (consumed > 0) {
                                e.requestMore(consumed);
                            }
                            // if drain to completion, ask for the next subscriber
                            if (e.done && q.isEmpty()) {
                                removeInner(e);
                                if (maxConcurrent != Integer.MAX_VALUE) {
                                    subscription.request(1);
                                }
                                continue outer;
                            }
                            
                            if (r == 0) {
                                break;
                            }
                        }
                    }

                    synchronized (this) {
                        if (!missed) {
                            skipFinal = true;
                            emitting = false;
                            break;
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
        
        void reportErrors(final Subscriber<? super T> child) {
            List<Throwable> throwables = new ArrayList<>();
            for (;;) {
                Throwable t = errors.poll();
                if (t == null) {
                    break;
                }
                throwables.add(t);
            }
            if (throwables.size() == 1) {
                child.onError(throwables.get(0));
            } else {
                child.onError(new CompositeException(throwables));
            }
        }
        
        /**
         * Subscriber that is subscribed to each Flowable received through oNext.
         */
        final class InnerSubscriber extends AbstractSubscriber<T> {
            final SpscArrayQueue<T> queue;
            final int index;
            Disposable disposable;
            volatile boolean done;
            public InnerSubscriber(int index) {
                this.index = index;
                this.queue = new SpscArrayQueue<>(Flow.defaultBufferSize());
            }
            @Override
            protected void onSubscribe() {
                disposable = Disposable.from(subscription);
                MergeSubscriber.this.composite.add(disposable);
                subscription.request(Flow.defaultBufferSize());
            }
            
            void requestMore(long n) {
                subscription.request(n);
            }
            
            @Override
            public void onNext(T item) {
                Conformance.itemNonNull(item);
                Conformance.subscriptionNonNull(subscription);
                if (done) {
                    return;
                }
                if (!queue.offer(item)) {
                    Conformance.mustRequestFirst(this);
                }
                drain();
            }
            @Override
            public void onError(Throwable throwable) {
                Conformance.throwableNonNull(throwable);
                Conformance.subscriptionNonNull(subscription);
                if (done) {
                    return;
                }
                done = true;
                subscription.cancel();
                errorInner(this, throwable);
            }
            @Override
            public void onComplete() {
                Conformance.subscriptionNonNull(subscription);
                if (done) {
                    return;
                }
                done = true;
                subscription.cancel();
                completeInner(this);
            }
        }
    }
}
