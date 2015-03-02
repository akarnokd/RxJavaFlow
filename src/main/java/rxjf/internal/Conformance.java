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
 * <p>
 * Conformance checking can be disabled by setting the {@code rxjf.conformance-checking}
 * system property to false.
 */
public final class Conformance {
    /** Enable/disable conformance checking globally to save performance. Default: {@code true}. */
    static final boolean CONFORMANCE_CHECKS;
    static {
        String csr = System.getProperty("rxjf.conformance-checking");
        CONFORMANCE_CHECKS = csr == null || "true".equals(csr);
    }
    private Conformance() {
        throw new IllegalStateException("No instances!");
    }
    /**
     * Verifies Rule &#xa7;3.9: Request amount MUST be positive.
     * @param n the requested amount
     * @param subscriber the subscriber requesting
     * @return true if the requrest amount is valid, false otherwise
     */
    public static boolean requestPositive(long n, Subscriber<?> subscriber) {
        if (!CONFORMANCE_CHECKS) {
            return true;
        }
        if (n <= 0) {
            subscriber.onError(new IllegalArgumentException("Rule \u00a73.9: Request amount MUST be positive: " + n));
            return false;
        }
        return true;
    }
    /**
     * Verifies Rule &#xa7;2.13: OnNext item MUST NOT be null.
     * @param item
     * @return
     */
    public static <T> T itemNonNull(T item) {
        if (!CONFORMANCE_CHECKS) {
            return item;
        }
        return Objects.requireNonNull(item, "Rule \u00a72.13: OnNext item MUST NOT be null");
    }
    /**
     * Verifies Rule &#xa7;1.9: Subscriber MUST NOT be null.
     * @param subscriber
     * @return
     */
    public static <T> Subscriber<T> subscriberNonNull(Subscriber<T> subscriber) {
        if (!CONFORMANCE_CHECKS) {
            return subscriber;
        }
        return Objects.requireNonNull(subscriber, "Rule \u00a71.9: Subscriber MUST NOT be null");
    }
    /**
     * Verifies Rule &#xa7;1.9: Subscription MUST NOT be null.
     * @param subscription
     * @return
     */
    public static Subscription subscriptionNonNull(Subscription subscription) {
        if (!CONFORMANCE_CHECKS) {
            return subscription;
        }
        return Objects.requireNonNull(subscription, "Rule \u00a71.9: Subscription MUST NOT be null");
    }
    /**
     * Verifies Rule &#xa7;2.12: onSubscribe MUST be called at most once.
     * @param currentSubscription the current subscription
     * @param subscriber the subscriber to report the violation
     * @return true if the subscription is valid (null), false otherwise
     */
    public static boolean onSubscribeOnce(Subscription currentSubscription, Subscriber<?> subscriber) {
        if (!CONFORMANCE_CHECKS) {
            return true;
        }
        if (currentSubscription != null) {
            subscriber.onError(new IllegalArgumentException("Rule \u00a72.12: onSubscribe MUST be called at most once"));
            return false;
        }
        return true;
    }
    /**
     * Verifies Rule &#xa7;1.9: Throwable MUST NOT be null.
     * @param throwable the throwable to validate
     * @return the throwable itself if it passed the null check
     */
    public static Throwable throwableNonNull(Throwable throwable) {
        if (!CONFORMANCE_CHECKS) {
            return throwable;
        }
        return Objects.requireNonNull(throwable, "Rule \u00a71.9: Throwable MUST NOT be null");
    }
}
