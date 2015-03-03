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
import rxjf.Flow.Subscription;
import rxjf.disposables.*;
import rxjf.internal.Conformance;
/**
 * 
 */
public abstract class AbstractDisposableSubscriber<T> implements DisposableSubscriber<T> {
    volatile Subscription subscription;
    static final long SUBSCRIPTION = addressOf(AbstractDisposableSubscriber.class, "subscription");
    static final Subscription TERMINATED = new Subscription() {
        @Override
        public void request(long n) {
        }
        @Override
        public void cancel() {
            
        }
    };
    final CompositeDisposable composite = new CompositeDisposable();
    
    @Override
    public final void onSubscribe(Subscription subscription) {
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
                internalOnSubscribe(subscription);
                return;
            }
        }
        
    }
    @Override
    public final void onNext(T item) {
        Conformance.itemNonNull(item);
        Conformance.subscriptionNonNull(this.subscription);
        internalOnNext(item);
    }
    @Override
    public final void onError(Throwable throwable) {
        try {
            Conformance.throwableNonNull(throwable);
            Conformance.subscriptionNonNull(this.subscription);
            internalOnError(throwable);
        } finally {
            dispose();
        }
    }
    @Override
    public final void onComplete() {
        try {
            Conformance.subscriptionNonNull(this.subscription);
            internalOnComplete();
        } finally {
            dispose();
        }
    }
    @Override
    public final boolean isDisposed() {
        return subscription == TERMINATED;
    }
    @Override
    public final void dispose() {
        if (subscription != TERMINATED) {
            Subscription s = (Subscription)UNSAFE.getAndSetObject(this, SUBSCRIPTION, TERMINATED);
            if (s != TERMINATED) {
                composite.dispose();
                if (s != null) {
                    s.cancel();
                }
                internalDispose();
            }
        }
    }
    
    @Override
    public final void add(Disposable disposable) {
        composite.add(disposable);
    }
    
    /**
     * Override this method to handle the first successful onSubscribe call.
     * <p>
     * The default implementation requests Long.MAX_VALUE.
     * @param subscription
     */
    protected void internalOnSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }
    /**
     * Override this method to consume an item.
     * <p>
     * The method is called after conformance checks so item is never null.
     * @param item
     */
    protected abstract void internalOnNext(T item);
    /**
     * Override this method to consume an throwable.
     * <p>
     * The method is called after conformance checks so throwable is never null.
     * @param item
     */
    protected abstract void internalOnError(Throwable throwable);
    /**
     * Override this method to consume a completion signal.
     */
    protected abstract void internalOnComplete();
    
    /**
     * Override this method to perform cleanup.
     */
    protected void internalDispose() {
        
    }
    
    public SerializedSubscriber<T> toSerialized() {
        return SerializedSubscriber.wrap(this);
    }
}
