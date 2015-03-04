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
import rxjf.*;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Returns an Flowable that skips the first <code>num</code> items emitted by the source
 * Flowable.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/skip.png" alt="">
 * <p>
 * You can ignore the first <code>num</code> items emitted by an Flowable and attend only to
 * those items that come after, by modifying the Flowable with the {@code skip} operator.
 */
public final class OperatorSkip<T> implements Flowable.Operator<T, T> {
    final int toSkip;

    public OperatorSkip(int n) {
        this.toSkip = n;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {
        return new SkipSubscriber<>(child, toSkip);
    }
    
    /**
     * Subscriber for the upstream.
     *
     * @param <T> the value type
     */
    static final class SkipSubscriber<T> extends AbstractSubscriber<T> {
        /** The actual subscriber. */
        final Subscriber<? super T> actual;
        /** Counts the remaining items to be skipped. */
        int remaining;
        
        public SkipSubscriber(Subscriber<? super T> actual, int skip) {
            this.actual = actual;
            this.remaining = skip;
        }
        
        @Override
        protected void onSubscribe() {
            actual.onSubscribe(subscription);
        }
        
        @Override
        public void onNext(T item) {
            if (remaining <= 0) {
                actual.onNext(item);
            } else {
                remaining--;
                subscription.request(1);
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
