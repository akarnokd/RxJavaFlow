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

import rxjf.Flow.*;
import rxjf.internal.Conformance;

/**
 * 
 */
public final class CheckedSubscriber<T> extends AbstractSubscriber<T> {
    final Subscriber<? super T> actual;

    private CheckedSubscriber(Subscriber<? super T> actual) {
        this.actual = Conformance.subscriberNonNull(actual);
    }
    
    public static <T> Subscriber<T> wrap(Subscriber<T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        if (subscriber instanceof CheckedSubscriber) {
            return subscriber;
        } else
        if (subscriber instanceof SafeSubscriber) {
            return subscriber;
        } else
        if (subscriber instanceof SerializedSubscriber) {
            return subscriber;
        }
        return new CheckedSubscriber<>(subscriber);
    }
    
    @Override
    public void onNext(T item) {
        Conformance.itemNonNull(item);
        Conformance.subscriptionNonNull(subscription);
        actual.onNext(item);
    }
    @Override
    public void onError(Throwable throwable) {
        Conformance.itemNonNull(throwable);
        Conformance.subscriptionNonNull(subscription);
        actual.onError(throwable);
    }
    @Override
    public void onComplete() {
        Conformance.subscriptionNonNull(subscription);
        actual.onComplete();
    }
    
    @Override
    public CheckedSubscriber<T> toChecked() {
        return this;
    }
}
