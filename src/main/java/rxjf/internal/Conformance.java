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

package rxjf.internal;

import java.util.Objects;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;

/**
 * Offers standard conformance checking methods that report errors
 * with proper message contents.
 */
public final class Conformance {
    private Conformance() {
        throw new IllegalStateException("No instances!");
    }
    /**
     * Verifies Rule §3.9: Request amount MUST be positive.
     * @param n the requested amount
     * @param subscriber the subscriber requesting
     * @return true if the requrest amount is valid, false otherwise
     */
    public static boolean requestPositive(long n, Subscriber<?> subscriber) {
        if (n <= 0) {
            subscriber.onError(new IllegalArgumentException("Rule §3.9: Request amount MUST be positive: " + n));
            return false;
        }
        return true;
    }
    /**
     * Verifies Rule §2.13: OnNext item MUST NOT be null.
     * @param item
     * @return
     */
    public static <T> T itemNonNull(T item) {
        return Objects.requireNonNull(item, "Rule §2.13: OnNext item MUST NOT be null");
    }
    /**
     * Verifies Rule §1.9: Subscriber MUST NOT be null.
     * @param subscriber
     * @return
     */
    public static <T> Subscriber<T> subscriberNonNull(Subscriber<T> subscriber) {
        return Objects.requireNonNull(subscriber, "Rule §1.9: Subscriber MUST NOT be null");
    }
    /**
     * Verifies Rule §1.9: Subscription MUST NOT be null.
     * @param subscription
     * @return
     */
    public static Subscription subscriptionNonNull(Subscription subscription) {
        return Objects.requireNonNull(subscription, "Rule §1.9: Subscription MUST NOT be null");
    }
    /**
     * Verifies Rule §2.12: onSubscribe MUST be called at most once.
     * @param currentSubscription
     * @param subscriber
     * @return
     */
    public static boolean onSubscribeOnce(Subscription currentSubscription, Subscriber<?> subscriber) {
        if (currentSubscription != null) {
            subscriber.onError(new IllegalArgumentException("Rule §2.12: onSubscribe MUST be called at most once"));
            return false;
        }
        return true;
    }
    /**
     * Verifies Rule §1.9: Throwable MUST NOT be null.
     * @param subscriber
     * @return
     */
    public static Throwable throwableNonNull(Throwable subscriber) {
        return Objects.requireNonNull(subscriber, "Rule §1.9: Throwable MUST NOT be null");
    }
}
