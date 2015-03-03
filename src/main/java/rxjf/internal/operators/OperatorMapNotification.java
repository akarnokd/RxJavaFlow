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

import java.util.function.*;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.Operator;
import rxjf.exceptions.OnErrorThrowable;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Applies a function of your choosing to every item emitted by an {@code Flowable}, and emits the results of
 * this transformation as a new {@code Flowable}.
 * <p>
 * <img width="640" height="305" src="https://raw.githubusercontent.com/wiki/ReactiveX/RxJava/images/rx-operators/map.png" alt="">
 */
public final class OperatorMapNotification<T, R> implements Operator<R, T> {

    private final Function<? super T, ? extends R> onNext;
    private final Function<? super Throwable, ? extends R> onError;
    private final Supplier<? extends R> onComplete;

    public OperatorMapNotification(Function<? super T, ? extends R> onNext, 
            Function<? super Throwable, ? extends R> onError, 
            Supplier<? extends R> onComplete) {
        this.onNext = onNext;
        this.onError = onError;
        this.onComplete = onComplete;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super R> o) {
        return new AbstractSubscriber<T>() {
            @Override
            protected void onSubscribe() {
                o.onSubscribe(subscription);
            }
            @Override
            public void onComplete() {
                try {
                    o.onNext(onComplete.get());
                    o.onComplete();
                } catch (Throwable e) {
                    o.onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                try {
                    o.onNext(onError.apply(e));
                    o.onComplete();
                } catch (Throwable e2) {
                    o.onError(e);
                }
            }

            @Override
            public void onNext(T t) {
                try {
                    o.onNext(onNext.apply(t));
                } catch (Throwable e) {
                    o.onError(OnErrorThrowable.addValueAsLastCause(e, t));
                }
            }

        };
    }

}
