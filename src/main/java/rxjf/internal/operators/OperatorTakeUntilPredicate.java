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
 * Returns an Flowable that emits items emitted by the source Flowable until
 * the provided predicate returns true.
 * <p>
 */
public final class OperatorTakeUntilPredicate<T> implements Operator<T, T> {
    final Predicate<? super T> stopPredicate;
    public OperatorTakeUntilPredicate(Predicate<? super T> stopPredicate) {
        this.stopPredicate = stopPredicate;
    }
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {
        return new AbstractSubscriber<T>() {
            boolean done;
            @Override
            protected void onSubscribe() {
                child.onSubscribe(subscription);
            }
            @Override
            public void onNext(T item) {
                Conformance.itemNonNull(item);
                Conformance.subscriptionNonNull(subscription);
                if (done) {
                    return;
                }
                
                child.onNext(item);
                
                boolean stop;
                try {
                    stop = stopPredicate.test(item);
                } catch (Throwable t) {
                    onError(t);
                    return;
                }
                if (stop) {
                    if (done) {
                        return;
                    }
                    done = true;
                    try {
                        child.onComplete();
                    } catch (Throwable e) {
                        Exceptions.handleUncaught(Conformance.onCompleteThrew(e));
                    }
                }
            }
            @Override
            public void onError(Throwable throwable) {
                Conformance.throwableNonNull(throwable);
                Conformance.subscriptionNonNull(subscription);
                if (done) {
                    return;
                }
                done = true;
                child.onError(throwable);
            }
            @Override
            public void onComplete() {
                Conformance.subscriptionNonNull(subscription);
                if (done) {
                    return;
                }
                done = true;
                child.onComplete();
            }
        };
    }
}
