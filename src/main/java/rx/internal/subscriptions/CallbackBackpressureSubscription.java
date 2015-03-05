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

package rx.internal.subscriptions;

import java.util.Objects;
import java.util.function.*;

import rx.Flow.Subscriber;

/**
 * A subscription that coordinates value production (offer) and
 * value emission (onXXX) for a subscriber while respecting the subscriber's requests
 * and uses callbacks to manage the enqueueing and dequeueing of values..
 */
public final class CallbackBackpressureSubscription<T> extends AbstractBackpressureSubscription<T> {
    final Predicate<? super T> onOffer;
    final Supplier<? extends T> onPeek;
    final Supplier<? extends T> onPoll;
    public CallbackBackpressureSubscription(
            Subscriber<? super T> subscriber,
            Predicate<? super T> onOffer,
            final Supplier<? extends T> onPeek,
            final Supplier<? extends T> onPoll) {
        super(subscriber);
        this.onOffer = Objects.requireNonNull(onOffer);
        this.onPeek = Objects.requireNonNull(onPeek);
        this.onPoll = Objects.requireNonNull(onPoll);
    }
    
    @Override
    protected boolean offer(T e) {
        return onOffer.test(e);
    }
    @Override
    protected T peek() {
        return onPeek.get();
    }
    @Override
    protected T poll() {
        return onPoll.get();
    }
}