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
import rxjf.exceptions.Exceptions;
import rxjf.internal.Conformance;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Skips any emitted source items as long as the specified condition holds true. Emits all further source items
 * as soon as the condition becomes false.
 */
public final class OperatorSkipWhile<T> implements Operator<T, T> {
    final Predicate<? super T> predicate;

    public OperatorSkipWhile(Predicate<? super T> predicate) {
        this.predicate = predicate;
    }
    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> child) {
        return new SkipWhileSubscriber<>(child, predicate);
    }
    
    /**
     * Subscriber skipping while the predicate returns false.
     * @param <T> the value type passing through
     */
    static final class SkipWhileSubscriber<T> extends AbstractSubscriber<T> {
        final Subscriber<? super T> actual;
        final Predicate<? super T> predicate;
        boolean passThru;
        public SkipWhileSubscriber(Subscriber<? super T> actual, Predicate<? super T> predicate) {
            this.actual = actual;
            this.predicate = predicate;
        }
        @Override
        protected void onSubscribe() {
            actual.onSubscribe(subscription);
        }
        @Override
        public void onNext(T item) {
            if (passThru) {
                actual.onNext(item);
            } else {
                boolean test;
                try {
                    test = predicate.test(item);
                } catch (Throwable t) {
                    try {
                        onError(t);
                    } catch (Throwable t2) {
                        Exceptions.handleUncaught(t);
                        Exceptions.handleUncaught(Conformance.onErrorThrew(t2));
                    }
                    return;
                }
                if (test) {
                    subscription.request(1);
                } else {
                    passThru = true;
                    actual.onNext(item);
                }
            }
        }
        @Override
        public void onError(Throwable throwable) {
            actual.onError(throwable);
        }
        @Override
        public void onComplete() {
            actual.onComplete();
        }
    }
}
