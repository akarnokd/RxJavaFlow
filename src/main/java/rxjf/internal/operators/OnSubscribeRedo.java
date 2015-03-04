/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rxjf.internal.operators;

/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static rx.Flowable.create;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import rx.Notification;
import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Flowable.Operator;
import rx.Producer;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Function;
import rx.functions.BiFunction;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.SerialSubscription;

public final class OnSubscribeRedo<T> implements OnSubscribe<T> {

    static final Function<Flowable<? extends Notification<?>>, Flowable<?>> REDO_INIFINITE = new Function<Flowable<? extends Notification<?>>, Flowable<?>>() {
        @Override
        public Flowable<?> call(Flowable<? extends Notification<?>> ts) {
            return ts.map(new Function<Notification<?>, Notification<?>>() {
                @Override
                public Notification<?> call(Notification<?> terminal) {
                    return Notification.createOnNext(null);
                }
            });
        }
    };

    public static final class RedoFinite implements Function<Flowable<? extends Notification<?>>, Flowable<?>> {
        private final long count;

        public RedoFinite(long count) {
            this.count = count;
        }

        @Override
        public Flowable<?> call(Flowable<? extends Notification<?>> ts) {
            return ts.map(new Function<Notification<?>, Notification<?>>() {

                int num=0;
                
                @Override
                public Notification<?> call(Notification<?> terminalNotification) {
                    if(count == 0) {
                        return terminalNotification;
                    }
                    
                    num++;
                    if(num <= count) {
                        return Notification.createOnNext(num);
                    } else {
                        return terminalNotification;
                    }
                }
                
            }).dematerialize();
        }
    }

    public static final class RetryWithPredicate implements Function<Flowable<? extends Notification<?>>, Flowable<? extends Notification<?>>> {
        private BiFunction<Integer, Throwable, Boolean> predicate;

        public RetryWithPredicate(BiFunction<Integer, Throwable, Boolean> predicate) {
            this.predicate = predicate;
        }

        @Override
        public Flowable<? extends Notification<?>> call(Flowable<? extends Notification<?>> ts) {
            return ts.scan(Notification.createOnNext(0), new BiFunction<Notification<Integer>, Notification<?>, Notification<Integer>>() {
                @SuppressWarnings("unchecked")
                @Override
                public Notification<Integer> call(Notification<Integer> n, Notification<?> term) {
                    final int value = n.getValue();
                    if (predicate.call(value, term.getThrowable()).booleanValue())
                        return Notification.createOnNext(value + 1);
                    else
                        return (Notification<Integer>) term;
                }
            });
        }
    }

    public static <T> Flowable<T> retry(Flowable<T> source) {
        return retry(source, REDO_INIFINITE);
    }

    public static <T> Flowable<T> retry(Flowable<T> source, final long count) {
        if (count < 0)
            throw new IllegalArgumentException("count >= 0 expected");
        if (count == 0)
            return source;
        return retry(source, new RedoFinite(count));
    }

    public static <T> Flowable<T> retry(Flowable<T> source, Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> notificationHandler) {
        return create(new OnSubscribeRedo<T>(source, notificationHandler, true, false, Schedulers.trampoline()));
    }

    public static <T> Flowable<T> retry(Flowable<T> source, Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> notificationHandler, Scheduler scheduler) {
        return create(new OnSubscribeRedo<T>(source, notificationHandler, true, false, scheduler));
    }

    public static <T> Flowable<T> repeat(Flowable<T> source) {
        return repeat(source, Schedulers.trampoline());
    }

    public static <T> Flowable<T> repeat(Flowable<T> source, Scheduler scheduler) {
        return repeat(source, REDO_INIFINITE, scheduler);
    }

    public static <T> Flowable<T> repeat(Flowable<T> source, final long count) {
        return repeat(source, count, Schedulers.trampoline());
    }

    public static <T> Flowable<T> repeat(Flowable<T> source, final long count, Scheduler scheduler) {
        if(count == 0) {
            return Flowable.empty();
        }
        if (count < 0)
            throw new IllegalArgumentException("count >= 0 expected");
        return repeat(source, new RedoFinite(count - 1), scheduler);
    }

    public static <T> Flowable<T> repeat(Flowable<T> source, Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> notificationHandler) {
        return create(new OnSubscribeRedo<T>(source, notificationHandler, false, true, Schedulers.trampoline()));
    }

    public static <T> Flowable<T> repeat(Flowable<T> source, Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> notificationHandler, Scheduler scheduler) {
        return create(new OnSubscribeRedo<T>(source, notificationHandler, false, true, scheduler));
    }

    public static <T> Flowable<T> redo(Flowable<T> source, Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> notificationHandler, Scheduler scheduler) {
        return create(new OnSubscribeRedo<T>(source, notificationHandler, false, false, scheduler));
    }

    private Flowable<T> source;
    private final Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> controlHandlerFunction;
    private boolean stopOnComplete;
    private boolean stopOnError;
    private final Scheduler scheduler;

    private OnSubscribeRedo(Flowable<T> source, Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> f, boolean stopOnComplete, boolean stopOnError,
            Scheduler scheduler) {
        this.source = source;
        this.controlHandlerFunction = f;
        this.stopOnComplete = stopOnComplete;
        this.stopOnError = stopOnError;
        this.scheduler = scheduler;
    }

    @Override
    public void call(final Subscriber<? super T> child) {
        final AtomicBoolean isLocked = new AtomicBoolean(true);
        final AtomicBoolean resumeBoundary = new AtomicBoolean(true);
        // incremented when requests are made, decremented when requests are fulfilled
        final AtomicLong consumerCapacity = new AtomicLong(0l);
        final AtomicReference<Producer> currentProducer = new AtomicReference<Producer>();

        final Scheduler.Worker worker = scheduler.createWorker();
        child.add(worker);

        final SerialSubscription sourceSubscriptions = new SerialSubscription();
        child.add(sourceSubscriptions);

        final PublishSubject<Notification<?>> terminals = PublishSubject.create();

        final Action0 subscribeToSource = new Action0() {
            @Override
            public void call() {
                if (child.isUnsubscribed()) {
                    return;
                }

                Subscriber<T> terminalDelegatingSubscriber = new Subscriber<T>() {
                    @Override
                    public void onComplete() {
                        unsubscribe();
                        terminals.onNext(Notification.createonComplete());
                    }

                    @Override
                    public void onError(Throwable e) {
                        unsubscribe();
                        terminals.onNext(Notification.createOnError(e));
                    }

                    @Override
                    public void onNext(T v) {
                        if (consumerCapacity.get() != Long.MAX_VALUE) {
                            consumerCapacity.decrementAndGet();
                        }
                        child.onNext(v);
                    }

                    @Override
                    public void setProducer(Producer producer) {
                        currentProducer.set(producer);
                        long c = consumerCapacity.get();
                        if (c > 0) {
                            producer.request(c);
                        }
                    }
                };
                // new subscription each time so if it unsubscribes itself it does not prevent retries
                // by unsubscribing the child subscription
                sourceSubscriptions.set(terminalDelegatingSubscriber);
                source.unsafeSubscribe(terminalDelegatingSubscriber);
            }
        };

        // the observable received by the control handler function will receive notifications of onComplete() in the case of 'repeat' 
        // type operators or notifications of onError for 'retry' this is done by lifting in a custom operator to selectively divert 
        // the retry/repeat relevant values to the control handler
        final Flowable<?> restarts = controlHandlerFunction.call(
                terminals.lift(new Operator<Notification<?>, Notification<?>>() {
                    @Override
                    public Subscriber<? super Notification<?>> call(final Subscriber<? super Notification<?>> filteredTerminals) {
                        return new Subscriber<Notification<?>>(filteredTerminals) {
                            @Override
                            public void onComplete() {
                                filteredTerminals.onComplete();
                            }

                            @Override
                            public void onError(Throwable e) {
                                filteredTerminals.onError(e);
                            }

                            @Override
                            public void onNext(Notification<?> t) {
                                if (t.isOnComplete() && stopOnComplete)
                                    child.onComplete();
                                else if (t.isOnError() && stopOnError)
                                    child.onError(t.getThrowable());
                                else {
                                    isLocked.set(false);
                                    filteredTerminals.onNext(t);
                                }
                            }

                            @Override
                            public void setProducer(Producer producer) {
                                producer.request(Long.MAX_VALUE);
                            }
                        };
                    }
                }));

        // subscribe to the restarts observable to know when to schedule the next redo.
        worker.schedule(new Action0() {
            @Override
            public void call() {
                restarts.unsafeSubscribe(new Subscriber<Object>(child) {
                    @Override
                    public void onComplete() {
                        child.onComplete();
                    }

                    @Override
                    public void onError(Throwable e) {
                        child.onError(e);
                    }

                    @Override
                    public void onNext(Object t) {
                        if (!isLocked.get() && !child.isUnsubscribed()) {
                            if (consumerCapacity.get() > 0) {
                                worker.schedule(subscribeToSource);
                            } else {
                                resumeBoundary.compareAndSet(false, true);
                            }
                        }
                    }

                    @Override
                    public void setProducer(Producer producer) {
                        producer.request(Long.MAX_VALUE);
                    }
                });
            }
        });

        child.setProducer(new Producer() {

            @Override
            public void request(final long n) {
                long c = BackpressureUtils.getAndAddRequest(consumerCapacity, n);
                Producer producer = currentProducer.get();
                if (producer != null) {
                    producer.request(n);
                } else
                if (c == 0 && resumeBoundary.compareAndSet(true, false)) {
                    worker.schedule(subscribeToSource);
                }
            }
        });
        
    }
}
