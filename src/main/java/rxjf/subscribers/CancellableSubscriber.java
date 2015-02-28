/**
 * Copyright 2015 David Karnok
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rxjf.subscribers;

import static rxjf.internal.UnsafeAccess.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.cancellables.*;
import rxjf.internal.Conformance;
/**
 * 
 */
public final class CancellableSubscriber<T> implements Subscriber<T>, Cancellable {
    final Subscriber<? super T> actual;
    volatile Subscription subscription;
    static final long SUBSCRIPTION = addressOf(CancellableSubscriber.class, "subscription");
    static final Subscription TERMINATED = new Subscription() {
        @Override
        public void request(long n) {
        }
        @Override
        public void cancel() {
            
        }
    };
    final CompositeCancellable composite = new CompositeCancellable();
    
    public CancellableSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    /**
     * Wraps a regual Subscriber to a CancellableSubscriber or returns it
     * directly if already the right type.
     * @param actual
     * @return
     */
    public static <T> CancellableSubscriber<T> wrap(Subscriber<T> actual) {
        Conformance.subscriberNonNull(actual);
        if (actual instanceof CancellableSubscriber) {
            return (CancellableSubscriber<T>)actual;
        }
        return new CancellableSubscriber<>(actual);
    }
    @Override
    public void onSubscribe(Subscription subscription) {
        Conformance.subscriptionNonNull(subscription);
        for (;;) {
            Subscription curr = this.subscription;
            if (curr == TERMINATED) {
                subscription.cancel();
                return;
            }
            if (!Conformance.onSubscribeOnce(curr, this)) {
                curr.cancel();
                return;
            }
            if (UNSAFE.compareAndSwapObject(this, SUBSCRIPTION, null, subscription)) {
                actual.onSubscribe(subscription);
                return;
            }
        }
        
    }
    @Override
    public void onNext(T item) {
        Conformance.itemNonNull(item);
        Conformance.subscriptionNonNull(this.subscription);
        actual.onNext(item);
    }
    @Override
    public void onError(Throwable throwable) {
        try {
            Conformance.throwableNonNull(throwable);
            Conformance.subscriptionNonNull(this.subscription);
            actual.onError(throwable);
        } finally {
            cancel();
        }
    }
    @Override
    public void onComplete() {
        try {
            Conformance.subscriptionNonNull(this.subscription);
            actual.onComplete();
        } finally {
            cancel();
        }
    }
    @Override
    public boolean isCancelled() {
        return subscription == TERMINATED;
    }
    @Override
    public void cancel() {
        composite.cancel();
        Subscription s = (Subscription)UNSAFE.getAndSetObject(this, SUBSCRIPTION, TERMINATED);
        if (s != null) {
            s.cancel();
        }
    }
    public void add(Cancellable cancellable) {
        composite.add(cancellable);
    }
}
