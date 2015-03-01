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
import rxjf.disposables.*;
import rxjf.internal.Conformance;
/**
 * 
 */
public final class DisposableSubscriber<T> implements Subscriber<T>, Disposable {
    final Subscriber<? super T> actual;
    volatile Subscription subscription;
    static final long SUBSCRIPTION = addressOf(DisposableSubscriber.class, "subscription");
    static final Subscription TERMINATED = new Subscription() {
        @Override
        public void request(long n) {
        }
        @Override
        public void cancel() {
            
        }
    };
    final CompositeDisposable composite = new CompositeDisposable();
    
    public DisposableSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    /**
     * Wraps a regual Subscriber to a DisposableSubscriber or returns it
     * directly if already the right type.
     * @param actual
     * @return
     */
    public static <T> DisposableSubscriber<T> wrap(Subscriber<T> actual) {
        Conformance.subscriberNonNull(actual);
        if (actual instanceof DisposableSubscriber) {
            return (DisposableSubscriber<T>)actual;
        }
        return new DisposableSubscriber<>(actual);
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
                actual.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        subscription.request(n);
                    }
                    @Override
                    public void cancel() {
                        DisposableSubscriber.this.dispose();
                    }
                });
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
            dispose();
        }
    }
    @Override
    public void onComplete() {
        try {
            Conformance.subscriptionNonNull(this.subscription);
            actual.onComplete();
        } finally {
            dispose();
        }
    }
    @Override
    public boolean isDisposed() {
        return subscription == TERMINATED;
    }
    @Override
    public void dispose() {
        composite.dispose();
        Subscription s = (Subscription)UNSAFE.getAndSetObject(this, SUBSCRIPTION, TERMINATED);
        if (s != null) {
            s.cancel();
        }
    }
    public void add(Disposable disposable) {
        composite.add(disposable);
    }
}
