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

import java.util.*;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.Operator;
import rxjf.internal.*;
import rxjf.internal.subscriptions.QueueBackpressureSubscription;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Returns an Observable that emits the last <code>count</code> items emitted by the source Observable.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/last.png" alt="">
 */
public final class OperatorTakeLast<T> implements Operator<T, T> {

    private final int count;

    public OperatorTakeLast(int count) {
        if (count < 0) {
            throw new IndexOutOfBoundsException("count could not be negative");
        }
        this.count = count;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> subscriber) {
        Deque<T> deque = new ArrayDeque<>();
        QueueBackpressureSubscription<T> qs = new QueueBackpressureSubscription<>(subscriber, deque);

        return new AbstractSubscriber<T>() {

            // no backpressure up as it wants to receive and discard all but the last
            @Override
            public void onSubscribe() {
                // we do this to break the chain of the child subscriber being passed through
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onComplete() {
                qs.onComplete();
                // we onSubscribe only now that all values have been received
                // and let the QueueSubscription handle the requests
                subscriber.onSubscribe(qs);
            }

            @Override
            public void onError(Throwable e) {
                deque.clear();
                subscriber.onError(e);
            }

            @Override
            public void onNext(T value) {
                if (count == 0) {
                    // If count == 0, we do not need to put value into deque and
                    // remove it at once. We can ignore the value directly.
                    return;
                }
                if (deque.size() == count) {
                    deque.removeFirst();
                }
                deque.offerLast(value);
            }
        };
    }
}
