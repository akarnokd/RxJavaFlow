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
package rx.internal.operators;

import rx.Flowable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.Exceptions;
import rx.functions.Func0;
import rx.functions.Function;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

/**
 * Returns an Flowable that mirrors the source Flowable. If either the first
 * item emitted by the source Flowable or any subsequent item don't arrive
 * within time windows defined by provided Flowables, switch to the
 * <code>other</code> Flowable if provided, or emit a TimeoutException .
 */
public class OperatorTimeoutWithSelector<T, U, V> extends
        OperatorTimeoutBase<T> {

    public OperatorTimeoutWithSelector(
            final Func0<? extends Flowable<U>> firstTimeoutSelector,
            final Function<? super T, ? extends Flowable<V>> timeoutSelector,
            Flowable<? extends T> other) {
        super(new FirstTimeoutStub<T>() {

            @Override
            public Subscription call(
                    final TimeoutSubscriber<T> timeoutSubscriber,
                    final Long seqId, Scheduler.Worker inner) {
                if (firstTimeoutSelector != null) {
                    Flowable<U> o = null;
                    try {
                        o = firstTimeoutSelector.call();
                    } catch (Throwable t) {
                        Exceptions.throwIfFatal(t);
                        timeoutSubscriber.onError(t);
                        return Subscriptions.unsubscribed();
                    }
                    return o.unsafeSubscribe(new Subscriber<U>() {

                        @Override
                        public void onComplete() {
                            timeoutSubscriber.onTimeout(seqId);
                        }

                        @Override
                        public void onError(Throwable e) {
                            timeoutSubscriber.onError(e);
                        }

                        @Override
                        public void onNext(U t) {
                            timeoutSubscriber.onTimeout(seqId);
                        }

                    });
                } else {
                    return Subscriptions.unsubscribed();
                }
            }
        }, new TimeoutStub<T>() {

            @Override
            public Subscription call(
                    final TimeoutSubscriber<T> timeoutSubscriber,
                    final Long seqId, T value, Scheduler.Worker inner) {
                Flowable<V> o = null;
                try {
                    o = timeoutSelector.call(value);
                } catch (Throwable t) {
                    Exceptions.throwIfFatal(t);
                    timeoutSubscriber.onError(t);
                    return Subscriptions.unsubscribed();
                }
                return o.unsafeSubscribe(new Subscriber<V>() {

                    @Override
                    public void onComplete() {
                        timeoutSubscriber.onTimeout(seqId);
                    }

                    @Override
                    public void onError(Throwable e) {
                        timeoutSubscriber.onError(e);
                    }

                    @Override
                    public void onNext(V t) {
                        timeoutSubscriber.onTimeout(seqId);
                    }

                });
            }
        }, other, Schedulers.immediate());
    }

}
