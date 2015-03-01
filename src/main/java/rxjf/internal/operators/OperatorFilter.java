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

import java.util.function.Predicate;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.Operator;
import rxjf.exceptions.OnErrorThrowable;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Filters an Observable by discarding any items it emits that do not meet some test.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/filter.png" alt="">
 */
public final class OperatorFilter<T> implements Operator<T, T> {

    private final Predicate<? super T> predicate;

    public OperatorFilter(Predicate<? super T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> child) {
        return new AbstractSubscriber<T>() {
            @Override
            protected void onSubscribe() {
                child.onSubscribe(subscription);
            }
            @Override
            public void onComplete() {
                child.onComplete();
            }

            @Override
            public void onError(Throwable e) {
                child.onError(e);
            }

            @Override
            public void onNext(T t) {
                try {
                    if (predicate.test(t)) {
                        child.onNext(t);
                    } else {
                        // TODO consider a more complicated version that batches these
                        subscription.request(1);
                    }
                } catch (Throwable e) {
                    child.onError(OnErrorThrowable.addValueAsLastCause(e, t));
                }
            }

        };
    }

}
