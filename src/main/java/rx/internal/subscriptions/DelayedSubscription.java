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

import static rx.internal.UnsafeAccess.*;
import rx.Flow.*;
import rx.internal.Conformance;

/**
 * Subscription that accumulates requests and cancellation until a real subscription
 * is set via setActual.
 * <p>
 * This subscription can be used in places where the actual subscription value is delayed
 * or may never appear (such as with {@link rxjf.processors.Processor}s} and
 * operating a Subscription may be required in the meantime.
 */
public final class DelayedSubscription implements Subscription {
    final Subscriber<?> subscriber;
    volatile Subscription actual;
    static final long ACTUAL = addressOf(DelayedSubscription.class, "actual");
    volatile long requested;
    static final long REQUESTED = addressOf(DelayedSubscription.class, "requested");
    
    static final long CANCELLED = Long.MIN_VALUE;
    static final long REPLACED = Long.MIN_VALUE / 2;
    
    /**
     * Creates a DelayedSubscription with the given {@link Subscriber} as the target
     * for reporting conformance violations.
     * @param subscriber the target Subscriber
     */
    public DelayedSubscription(Subscriber<?> subscriber) {
        this.subscriber = Conformance.subscriberNonNull(subscriber);
    }
    @Override
    public void request(long n) {
        if (!Conformance.requestPositive(n, subscriber)) {
            cancel();
            return;
        }
        for (;;) {
            Subscription a = actual;
            if (a != null) {
                a.request(n);
                return;
            }
            long r = requested;
            if (r == REPLACED) {
                // actual may be late, loop until becomes visible
                continue;
            }
            if (r == CANCELLED) {
                break;
            }
            long u = r + n;
            if (u < 0) {
                u = Long.MAX_VALUE;
            }
            if (UNSAFE.compareAndSwapLong(this, REQUESTED, r, u)) {
                break;
            }
        }
    }
    @Override
    public void cancel() {
        for (;;) {
            Subscription a = actual;
            if (a != null) {
                a.cancel();
                return;
            }
            long r = requested;
            if (r == REPLACED) {
                // actual may be late, loop until becomes visible
                continue;
            }
            if (UNSAFE.compareAndSwapLong(this, REQUESTED, r, CANCELLED)) {
                break;
            }
        }
    }
    public void setActual(Subscription actual) {
        Conformance.subscriptionNonNull(actual);
        for (;;) {
            Subscription a = this.actual;
            if (!Conformance.onSubscribeOnce(a, subscriber)) {
                cancel();
                break;
            }
            long r = requested;
            if (r == REPLACED) {
                continue;
            }
            if (r == CANCELLED) {
                actual.cancel();
                break;
            }
            if (UNSAFE.compareAndSwapLong(this, REQUESTED, r, REPLACED)) {
                this.actual = actual;
                if (r > 0) {
                    actual.request(r);
                }
                break;
            }
        }
    }
}