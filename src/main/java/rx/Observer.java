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
package rx;

import rx.Flow.*;

/**
 * Provides a mechanism for receiving push-based notifications in an unbounded fashion.
 * <p>
 * After an Observer calls an {@link Observable}'s {@link Observable#subscribe subscribe} method, the
 * {@code Observable} calls the Observer's {@link #onNext} method to provide notifications. A well-behaved
 * {@code Observable} will call an Observer's {@link #onCompleted} method exactly once or the Observer's
 * {@link #onError} method exactly once.
 * 
 * @see <a href="http://reactivex.io/documentation/observable.html">ReactiveX documentation: Observable</a>
 * @param <T>
 *          the type of item the Observer expects to observe
 */
public interface Observer<T> extends Subscriber<T> {
    @Override
    default void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }
}
