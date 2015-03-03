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
package rxjf.internal.subscriptions;

import static rxjf.internal.UnsafeAccess.*;
import rxjf.Flow.*;
import rxjf.internal.Conformance;

/**
 * A runnable subscription which emits a single value once given to the Subscriber via
 * onSubscribe or scheduled on a Scheduler.
 *
 * @param <T> the value type returned
 */
public final class ScalarSubscription<T> implements Runnable, Subscription {
    private final Subscriber<? super T> subscriber;
    private final T value;
    volatile int once;
    static final long ONCE = addressOf(ScalarSubscription.class, "once");

    public ScalarSubscription(Subscriber<? super T> subscriber,
            T value) {
        this.subscriber = subscriber;
        this.value = value;
    }

    @Override
    public void run() {
        subscriber.onSubscribe(this);
    }
    @Override
    public void request(long n) {
        if (!Conformance.requestPositive(n, subscriber)) {
            cancel();
            return;
        }
        if (UNSAFE.getAndSetInt(this, ONCE, 1) == 0) {
            try {
                subscriber.onNext(value);
            } catch (Throwable t) {
                subscriber.onError(t);
                return;
            }
            subscriber.onComplete();
        }
    }
    @Override
    public void cancel() {
        UNSAFE.getAndSetInt(this, ONCE, 1);
    }
}