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

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.Flowable.Operator;
import rxjf.schedulers.Scheduler;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Subscribes Observers on the specified {@code Scheduler}.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/subscribeOn.png" alt="">
 */
public final class OperatorSubscribeOn<T> implements Operator<T, Flowable<T>> {

    private final Scheduler scheduler;

    public OperatorSubscribeOn(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Subscriber<? super Flowable<T>> apply(final Subscriber<? super T> subscriber) {
        final Scheduler.Worker inner = scheduler.createWorker();
        return new AbstractSubscriber<Flowable<T>>() {

            @Override
            protected void onSubscribe() {
                subscription.request(1);
            }
            
            @Override
            public void onComplete() {
                // ignore because this is a nested Flowable and we expect only 1 Flowable<T> emitted to onNext
            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }

            @Override
            public void onNext(final Flowable<T> o) {
                inner.schedule(new Runnable() {

                    @Override
                    public void run() {
                        final Thread t = Thread.currentThread();
                        o.unsafeSubscribe(new AbstractSubscriber<T>() {

                            @Override
                            protected void onSubscribe() {
                                subscriber.onSubscribe(new Subscription() {
                                    @Override
                                    public void request(long n) {
                                        if (t == Thread.currentThread()) {
                                            subscription.request(n);
                                        } else {
                                            inner.schedule(() -> subscription.request(n));
                                        }
                                    }
                                    @Override
                                    public void cancel() {
                                        subscription.cancel();
                                        inner.dispose();
                                    }
                                });
                            }
                            
                            @Override
                            public void onComplete() {
                                subscriber.onComplete();
                            }

                            @Override
                            public void onError(Throwable e) {
                                subscriber.onError(e);
                            }

                            @Override
                            public void onNext(T t) {
                                subscriber.onNext(t);
                            }
                        });
                    }
                });
            }

        };
    }
}
