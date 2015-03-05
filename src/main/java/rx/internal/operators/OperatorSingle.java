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

import java.util.NoSuchElementException;

import rx.Flow.Subscriber;
import rx.Observable.Operator;
import rx.subscribers.AbstractSubscriber;

/**
 * If the Observable completes after emitting a single item that matches a
 * predicate, return an Observable containing that item. If it emits more than
 * one such item or no item, throw an IllegalArgumentException.
 */
public final class OperatorSingle<T> implements Operator<T, T> {

    private final boolean hasDefaultValue;
    private final T defaultValue;

    public OperatorSingle() {
        this(false, null);
    }

    public OperatorSingle(T defaultValue) {
        this(true, defaultValue);
    }

    private OperatorSingle(boolean hasDefaultValue, final T defaultValue) {
        this.hasDefaultValue = hasDefaultValue;
        this.defaultValue = defaultValue;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> child) {
        return new AbstractSubscriber<T>() {

            private T value;
            private boolean isNonEmpty = false;
            private boolean hasTooManyElements = false;

            @Override
            protected void onSubscribe() {
                child.onSubscribe(subscription);
            }

            @Override
            public void onNext(T value) {
                if (isNonEmpty) {
                    hasTooManyElements = true;
                    child.onError(new IllegalArgumentException("Sequence contains too many elements"));
                    subscription.cancel();
                } else {
                    this.value = value;
                    isNonEmpty = true;
                    // Issue: https://github.com/ReactiveX/RxJava/pull/1527
                    // Because we cache a value and don't emit now, we need to request another one.
                    subscription.request(1);
                }
            }

            @Override
            public void onComplete() {
                if (hasTooManyElements) {
                    // We have already sent an onError message
                } else {
                    if (isNonEmpty) {
                        child.onNext(value);
                        child.onComplete();
                    } else {
                        if (hasDefaultValue) {
                            child.onNext(defaultValue);
                            child.onComplete();
                        } else {
                            child.onError(new NoSuchElementException("Sequence contains no elements"));
                        }
                    }
                }
            }

            @Override
            public void onError(Throwable e) {
                child.onError(e);
            }

        };
    }

}
