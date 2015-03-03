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
package rxjf.internal.operators;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import rxjf.*;
import rxjf.Flow.Processor;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.disposables.Disposable;
import rxjf.subscribers.AbstractDisposableSubscriber;

/**
 * Shares a single subscription to a source through a Processor.
 * 
 * @param <T>
 *            the source value type
 * @param <R>
 *            the result value type
 */
public final class OperatorMulticast<T, R> extends ConnectableFlowable<R> {
    final Flowable<? extends T> source;
    final Object guard;
    final Supplier<? extends Processor<? super T, ? extends R>> processorFactory;
    final AtomicReference<Processor<? super T, ? extends R>> connectedProcessor;
    final List<Subscriber<? super R>> waitingForConnect;

    /** Guarded by guard. */
    private AbstractDisposableSubscriber<T> subscriber;
    // wraps subscription above for unsubscription using guard
    private Disposable guardedSubscription;

    public OperatorMulticast(Flowable<? extends T> source, final Supplier<? extends Processor<? super T, ? extends R>> processorFactory) {
        this(new Object(), new AtomicReference<Processor<? super T, ? extends R>>(), new ArrayList<Subscriber<? super R>>(), source, processorFactory);
    }

    private OperatorMulticast(final Object guard, 
            final AtomicReference<Processor<? super T, ? extends R>> connectedProcessor, 
            final List<Subscriber<? super R>> waitingForConnect, 
            Flowable<? extends T> source, 
            final Supplier<? extends Processor<? super T, ? extends R>> processorFactory) {
        super(s -> {
            synchronized (guard) {
                Processor<? super T, ? extends R> processor = connectedProcessor.get();
                if (processor == null) {
                    // not connected yet, so register
                    waitingForConnect.add(s);
                } else {
                    if (processor instanceof Flowable) {
                        // we are already connected so subscribe directly
                        @SuppressWarnings("unchecked")
                        Flowable<R> p = (Flowable<R>)processor;
                        p.unsafeSubscribe(s);
                    } else {
                        processor.subscribe(s);
                    }
                }
            }
        });
        this.guard = guard;
        this.connectedProcessor = connectedProcessor;
        this.waitingForConnect = waitingForConnect;
        this.source = source;
        this.processorFactory = processorFactory;
    }

    @Override
    public void connect(Consumer<? super Disposable> connection) {
        // each time we connect we create a new Processor and Subscription

        // subscription is the state of whether we are connected or not
        synchronized (guard) {
            if (subscriber != null) {
                // already connected
                connection.accept(guardedSubscription);
                return;
            } else {
                // we aren't connected, so let's create a new Processor and connect
                final Processor<? super T, ? extends R> processor = processorFactory.get();
                
                // create new Subscriber that will pass-thru to the processor we just created
                // we do this since it is also a Subscription whereas the Processor is not
                subscriber = new AbstractDisposableSubscriber<T>() {
                    @Override
                    protected void internalOnSubscribe(Subscription subscription) {
                        processor.onSubscribe(subscription);
                    }
                    @Override
                    public void internalOnComplete() {
                        processor.onComplete();
                    }

                    @Override
                    public void internalOnError(Throwable e) {
                        processor.onError(e);
                    }

                    @Override
                    public void internalOnNext(T args) {
                        processor.onNext(args);
                    }
                };
                final AtomicReference<Disposable> gs = new AtomicReference<>();
                gs.set(Disposable.from(() -> {
                    Disposable s;
                    synchronized (guard) {
                        if (guardedSubscription != gs.get()) {
                            return;
                        }
                        s = subscriber;
                        subscriber = null;
                        guardedSubscription = null;
                        connectedProcessor.set(null);
                    }
                    if (s != null) {
                        s.dispose();
                    }
                }));
                guardedSubscription = gs.get();
                
                // register any subscribers that are waiting with this new processor
                if (processor instanceof Flowable) {
                    @SuppressWarnings("unchecked")
                    Flowable<R> p = (Flowable<R>)processor;
                    for(Subscriber<? super R> s : waitingForConnect) {
                        p.unsafeSubscribe(s);
                    }
                } else {
                    for(Subscriber<? super R> s : waitingForConnect) {
                        processor.subscribe(s);
                    }
                }
                // clear the waiting list as any new ones that come in after leaving this synchronized block will go direct to the Processor
                waitingForConnect.clear();
                // record the Processor so OnSubscribe can see it
                connectedProcessor.set(processor);
            }
            
        }

        // in the lock above we determined we should subscribe, do it now outside the lock
        // register a subscription that will shut this down
        connection.accept(guardedSubscription);

        // now that everything is hooked up let's subscribe
        // as long as the subscription is not null (which can happen if already unsubscribed)
        Subscriber<T> sub; 
        synchronized (guard) {
            sub = subscriber;
        }
        if (sub != null) {
            source.subscribe(sub);
        }
    }
}