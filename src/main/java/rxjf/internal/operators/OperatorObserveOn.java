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

import java.util.Queue;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.Flowable.Operator;
import rxjf.disposables.Disposable;
import rxjf.exceptions.MissingBackpressureException;
import rxjf.internal.*;
import rxjf.internal.queues.SpscArrayQueue;
import rxjf.internal.schedulers.*;
import rxjf.schedulers.Scheduler;
import rxjf.subscribers.AbstractSubscriber;

/**
 * 
 */
public final class OperatorObserveOn<T> implements Operator<T, T> {
    final Scheduler scheduler;
    public OperatorObserveOn(Scheduler scheduler) {
        this.scheduler = scheduler;
    }
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {
        if (scheduler instanceof ImmediateScheduler) {
            // avoid overhead, execute directly
            return child;
        } else if (scheduler instanceof TrampolineScheduler) {
            // avoid overhead, execute directly
            return child;
        }
        return new ObserveOnSubscriber<>(scheduler, child);
    }
    /** Observe through individual queue per observer. */
    private static final class ObserveOnSubscriber<T> extends AbstractSubscriber<T> {
        final Subscriber<? super T> child;
        final Scheduler.Worker recursiveScheduler;
        final ScheduledUnsubscribe scheduledUnsubscribe;
        final NotificationLite<T> on = NotificationLite.instance();

        final Queue<Object> queue;
        volatile boolean completed;
        volatile boolean failure;

        volatile long requested = 0;
        @SuppressWarnings("rawtypes")
        static final AtomicLongFieldUpdater<ObserveOnSubscriber> REQUESTED = AtomicLongFieldUpdater.newUpdater(ObserveOnSubscriber.class, "requested");

        @SuppressWarnings("unused")
        volatile long counter;
        @SuppressWarnings("rawtypes")
        static final AtomicLongFieldUpdater<ObserveOnSubscriber> COUNTER_UPDATER = AtomicLongFieldUpdater.newUpdater(ObserveOnSubscriber.class, "counter");

        volatile Throwable error;

        // do NOT pass the Subscriber through to couple the subscription chain ... unsubscribing on the parent should
        // not prevent anything downstream from consuming, which will happen if the Subscription is chained
        public ObserveOnSubscriber(Scheduler scheduler, Subscriber<? super T> child) {
            this.child = child;
            this.recursiveScheduler = scheduler.createWorker();
            queue = new SpscArrayQueue<>(Flow.defaultBufferSize());
            
            this.scheduledUnsubscribe = new ScheduledUnsubscribe(recursiveScheduler);
        }

        @Override
        public void onSubscribe() {
            child.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    Conformance.requestPositive(n, child);
                    REQUESTED.getAndAdd(ObserveOnSubscriber.this, n);
                    schedule();
                }
                @Override
                public void cancel() {
                    dispose();
                }
            });
            // signal that this is an async operator capable of receiving this many
            subscription.request(Flow.defaultBufferSize());
        }

        void dispose() {
            subscription.cancel();
            scheduledUnsubscribe.dispose();
            recursiveScheduler.dispose();
        }
        
        @Override
        public void onNext(final T t) {
            if (completed) {
                return;
            }
            if (!queue.offer(on.next(t))) {
                onError(new MissingBackpressureException());
                return;
            }
            schedule();
        }

        @Override
        public void onComplete() {
            if (completed) {
                return;
            }
            if (error != null) {
                return;
            }
            completed = true;
            schedule();
        }

        @Override
        public void onError(final Throwable e) {
            if (completed) {
                return;
            }
            if (error != null) {
                return;
            }
            error = e;
            // unsubscribe eagerly since time will pass before the scheduled onError results in an unsubscribe event
            dispose();
            // mark failure so the polling thread will skip onNext still in the queue
            completed = true;
            failure = true;
            schedule();
        }

        final Runnable action = this::pollQueue;
        
        protected void schedule() {
            if (COUNTER_UPDATER.getAndIncrement(this) == 0) {
                recursiveScheduler.schedule(action);
            }
        }

        // only execute this from schedule()
        void pollQueue() {
            int emitted = 0;
            do {
                /*
                 * Set to 1 otherwise it could have grown very large while in the last poll loop
                 * and then we can end up looping all those times again here before exiting even once we've drained
                 */
                counter = 1;

//                middle:
                while (!scheduledUnsubscribe.isDisposed()) {
                    if (failure) {
                        child.onError(error);
                        return;
                    } else {
                        if (requested == 0 && completed && queue.isEmpty()) {
                            child.onComplete();
                            return;
                        }
                        if (REQUESTED.getAndDecrement(this) != 0) {
                            Object o = queue.poll();
                            if (o == null) {
                                if (completed) {
                                    if (failure) {
                                        child.onError(error);
                                    } else {
                                        child.onComplete();
                                    }
                                    return;
                                }
                                // nothing in queue
                                REQUESTED.incrementAndGet(this);
                                break;
                            } else {
                                if (!on.accept(child, o)) {
                                    // non-terminal event so let's increment count
                                    emitted++;
                                }
                            }
                        } else {
                            // we hit the end ... so increment back to 0 again
                            REQUESTED.incrementAndGet(this);
                            break;
                        }
                    }
                }
            } while (COUNTER_UPDATER.decrementAndGet(this) > 0);

            // request the number of items that we emitted in this poll loop
            if (emitted > 0) {
                subscription.request(emitted);
            }
        }
    }

    static final class ScheduledUnsubscribe implements Disposable {
        final Scheduler.Worker worker;
        volatile int once;
        static final long ONCE = addressOf(ScheduledUnsubscribe.class, "once");
        volatile boolean unsubscribed = false;

        public ScheduledUnsubscribe(Scheduler.Worker worker) {
            this.worker = worker;
        }

        @Override
        public boolean isDisposed() {
            return unsubscribed;
        }

        @Override
        public void dispose() {
            if (UNSAFE.compareAndSwapInt(this, ONCE, 0, 1)) {
                worker.schedule(() -> {
                        worker.dispose();
                        unsubscribed = true;
                    }
                );
            }
        }

    }
}
