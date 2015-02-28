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
            return;
        }
        this.subscription = subscription;
        onSubscribe();
    }
    /**
     * Override this to react to an onSubscribe event from upstream.
     * The {@code subscription} is available
     */
    protected void onSubscribe() {
        
    }
    public CancellableSubscriber<T> toCancellable() {
        return CancellableSubscriber.wrap(this);
    }
    public SerializedSubscriber<T> toSerialized() {
        return SerializedSubscriber.wrap(this);
    }
    public CheckedSubscriber<T> toChecked() {
        return (CheckedSubscriber<T>)CheckedSubscriber.wrap(this);
    }
}
