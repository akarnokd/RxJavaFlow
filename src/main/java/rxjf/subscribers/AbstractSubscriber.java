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

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.Conformance;

/**
 * 
 */
public abstract class AbstractSubscriber<T> implements Subscriber<T> {
    protected Subscription subscription;
    @Override
    public final void onSubscribe(Subscription subscription) {
        Conformance.subscriptionNonNull(subscription);
        if (!Conformance.onSubscribeOnce(this.subscription, this)) {
            subscription.cancel();
            return;
        }
        this.subscription = subscription;
        onSubscribe();
    }
    /**
     * Override this to react to an onSubscribe event from upstream.
     * The default implementation starts an unbounded stream by requesting {@code Long.MAX_VALUE}.
     * The {@code subscription} is available.
     */
    protected void onSubscribe() {
        subscription.request(Long.MAX_VALUE);
    }
    public DisposableSubscriber<T> toDisposable() {
        return DisposableSubscriber.from(this);
    }
    public SerializedSubscriber<T> toSerialized() {
        return SerializedSubscriber.wrap(this);
    }
    
    /**
     * Returns a Subscriber that cancels any subscribers it receives
     * and does only basic null-checking for conformance.
     * @return the cancelled subscriber
     */
    @SuppressWarnings("unchecked")
    public static <T> Subscriber<T> cancelled() {
        return (Subscriber<T>)CANCELLED;
    }
    /** Single instance because it is stateless. */
    static final CancelledSubscriber CANCELLED = new CancelledSubscriber();
    /** A Subscriber that cancels any subscription immediately and ignores events. */
    static final class CancelledSubscriber implements Subscriber<Object> {
        @Override
        public void onSubscribe(Subscription subscription) {
            Conformance.subscriptionNonNull(subscription);
            subscription.cancel();
        }
        @Override
        public void onNext(Object item) {
            Conformance.itemNonNull(item);
        }
        @Override
        public void onError(Throwable throwable) {
            Conformance.throwableNonNull(throwable);
        }
        @Override
        public void onComplete() {
            
        }
    }
}
