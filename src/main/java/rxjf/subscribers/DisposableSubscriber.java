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
import rxjf.disposables.Disposable;

/**
 * Interface that combines the Subscriber and the Disposable interfaces
 * for subscribers that need to support external cancellations.
 * @param <T> the observed value type of the Subscriber
 */
public interface DisposableSubscriber<T> extends Subscriber<T>, Disposable {
    /**
     * Add a disposable resource to this disposable subscriber which will
     * be disposed when the subscriber cancels its subscription.
     * @param disposable the disposable to add
     */
    void add(Disposable disposable);
    /**
     * Wraps a general subscriber into a disposable subscriber or
     * returns it directly if already a disposable subscriber.
     * @param subscriber the subscriber to wrap
     * @return the disposable subscriber instance
     */
    @SuppressWarnings("unchecked")
    static <T> DisposableSubscriber<T> from(Subscriber<? super T> subscriber) {
        if (subscriber instanceof DisposableSubscriber) {
            return (DisposableSubscriber<T>)subscriber;
        }
        return new DefaultDisposableSubscriber<>(subscriber);
    }
}
