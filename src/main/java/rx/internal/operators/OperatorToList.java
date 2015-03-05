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

import java.util.*;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.Observable.Operator;
import rx.internal.Conformance;
import rx.subscribers.AbstractSubscriber;

/**
 * Returns an {@code Observable} that emits a single item, a list composed of all the items emitted by the
 * source {@code Observable}.
 * <p>
 * <img width="640" height="305" src="https://raw.githubusercontent.com/wiki/ReactiveX/RxJava/images/rx-operators/toList.png" alt="">
 * <p>
 * Normally, an {@code Observable} that returns multiple items will do so by invoking its subscriber's
 * {@link Subscriber#onNext onNext} method for each such item. You can change this behavior, instructing the
 * {@code Observable} to compose a list of all of these multiple items and then to invoke the subscriber's
 * {@code onNext} method once, passing it the entire list, by using this operator.
 * <p>
 * Be careful not to use this operator on {@code Observable}s that emit infinite or very large numbers of items,
 * as you do not have the option to unsubscribe.
 */
public final class OperatorToList<T> implements Operator<List<T>, T> {
    /** Lazy initialization via inner-class holder. */
    private static final class Holder {
        /** A singleton instance. */
        static final OperatorToList<Object> INSTANCE = new OperatorToList<>();
    }
    /**
     * @return a singleton instance of this stateless operator.
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> OperatorToList<T> instance() {
        return (OperatorToList<T>)Holder.INSTANCE;
    }
    private OperatorToList() { }
    @Override
    public Subscriber<? super T> apply(final Subscriber<? super List<T>> o) {
        return new AbstractSubscriber<T>() {

            private boolean completed = false;
            final List<T> list = new LinkedList<>();

            @Override
            public void onSubscribe() {
                Subscriber<T> self = this;
                o.onSubscribe(new Subscription() {
                    boolean once;
                    @Override
                    public void request(long n) {
                        if (!Conformance.requestPositive(n, self)) {
                            cancel();
                            return;
                        }
                        if (!once) {
                            once = true;
                            subscription.request(Long.MAX_VALUE);
                        }
                    }
                    @Override
                    public void cancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onComplete() {
                try {
                    completed = true;
                    o.onNext(new ArrayList<>(list));
                    o.onComplete();
                } catch (Throwable e) {
                    onError(e);
                }
            }

            @Override
            public void onError(Throwable e) {
                o.onError(e);
            }

            @Override
            public void onNext(T value) {
                if (!completed) {
                    list.add(value);
                }
            }

        };
    }

}
