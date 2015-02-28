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

import java.util.Objects;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.cancellables.Cancellable;
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
    
    
    private CancellableSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    public static <T> CancellableSubscriber<T> wrap(Subscriber<T> actual) {
        Conformance.subscriberNonNull(actual);
        if (actual instanceof CancellableSubscriber) {
            return (CancellableSubscriber<T>)actual;
        }
        return new CancellableSubscriber<>(actual);
    }
    @Override
    public void onSubscribe(Subscription subscription) {
        for (;;) {
            Subscription curr = subscription;
            if (curr == TERMINATED) {
                subscription.cancel();
                return;
            }
            if (!Conformance.onSubscribeOnce(curr, this)) {
                curr.cancel();
                return;
            }
        }
        
    }
    @Override
    public void onNext(T item) {
        // TODO Auto-generated method stub
        
    }
    @Override
    public void onError(Throwable throwable) {
        // TODO Auto-generated method stub
        
    }
    @Override
    public void onComplete() {
        // TODO Auto-generated method stub
        
    }
    @Override
    public boolean isCancelled() {
        return subscription == TERMINATED;
    }
    @Override
    public void cancel() {
        Subscription s = (Subscription)UNSAFE.getAndSetObject(this, SUBSCRIPTION, TERMINATED);
        if (s != null) {
            s.cancel();
        }
    }
}
