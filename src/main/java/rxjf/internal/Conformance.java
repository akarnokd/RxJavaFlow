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
import rxjf.exceptions.MissingBackpressureException;

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
            subscriber.onError(new IllegalStateException("Rule \u00a72.12: onSubscribe MUST be called at most once"));
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
    /**
     * Reports violation of Rule &#xa7;1.1: Producer MUST NOT produce an onNext event without a preceding request(n | n > 0).
     * @param subscriber the subscriber to report the error to
     */
    public static void mustRequestFirst(Subscriber<?> subscriber) {
        subscriber.onError(mustRequestFirst());
    }
    /**
     * Constructs a MissingBackpressureException with the rule text
     * Rule &#xa7;1.1: Producer MUST NOT produce an onNext event without a preceding request(n | n > 0)
     * @return "Rule &#xa7;1.1: Producer MUST NOT produce an onNext event without a preceding request(n | n > 0)"
     */
    public static MissingBackpressureException mustRequestFirst() {
        return new MissingBackpressureException("Rule \u00a71.1: Producer MUST NOT produce an onNext event without a preceding request(n | n > 0)");
    }
    /**
     * Creates an exception citing rule &#xa7;3.15: Subscription.cancel MUST return normally.
     * @param throwable the throwable thrown
     * @return the IllegalStateException with the exception and rule quote
     */
    public static IllegalStateException cancelThrew(Throwable throwable) {
        return new IllegalStateException("Rule \u00a73.15: Subscription.cancel MUST return normally", throwable);
    }
    /**
     * Creates an exception citing rule &#xa7;3.16: Subscription.request MUST return normally.
     * @param throwable the throwable thrown
     * @return the IllegalStateException with the exception and rule quote
     */
    public static IllegalStateException requestThrew(Throwable throwable) {
        return new IllegalStateException("Rule \u00a73.16: Subscription.request MUST return normally", throwable);
    }
    /**
     * Creates an exception citing rule &#xa7;2.13: Subscriber.onSubscribe MUST return normally.
     * @param throwable the throwable thrown
     * @return the IllegalStateException with the exception and rule quote
     */
    public static IllegalStateException onSubscribeThrew(Throwable throwable) {
        return new IllegalStateException("Rule \u00a72.13: Subscriber.onSubscribe MUST return normally", throwable);
    }
    /**
     * Creates an exception citing rule &#xa7;2.13: Subscriber.onError MUST return normally.
     * @param throwable the throwable thrown
     * @return the IllegalStateException with the exception and rule quote
     */
    public static IllegalStateException onErrorThrew(Throwable throwable) {
        return new IllegalStateException("Rule \u00a72.13: Subscriber.onError MUST return normally", throwable);
    }
    /**
     * Creates an exception citing rule &#xa7;2.13: Subscriber.onNext MUST return normally.
     * @param throwable the throwable thrown
     * @return the IllegalStateException with the exception and rule quote
     */
    public static IllegalStateException onNextThrew(Throwable throwable) {
        return new IllegalStateException("Rule \u00a72.13: Subscriber.onNext MUST return normally", throwable);
    }
    /**
     * Creates an exception citing rule &#xa7;2.13: Subscriber.onComplete MUST return normally.
     * @param throwable the throwable thrown
     * @return the IllegalStateException with the exception and rule quote
     */
    public static IllegalStateException onCompleteThrew(Throwable throwable) {
        return new IllegalStateException("Rule \u00a72.13: Subscriber.onComplete MUST return normally", throwable);
    }
}
