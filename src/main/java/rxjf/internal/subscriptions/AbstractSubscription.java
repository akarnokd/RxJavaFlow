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
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.disposables.Disposable;
import rxjf.internal.*;

/**
 * 
 */
public abstract class AbstractSubscription<T> implements Subscription, Disposable, SubscriptionState<T> {
    /** The current requested count, negative value indicates cancelled subscription. */
    private volatile long requested;
    private static final long REQUESTED = addressOf(AbstractSubscription.class, "requested");
    private final Subscriber<? super T> subscriber;
    public AbstractSubscription(Subscriber<? super T> subscriber) {
        this.subscriber = Conformance.subscriberNonNull(subscriber);
    }
    @Override
    public final boolean isDisposed() {
        return requested < 0;
    }
    @Override
    public final void dispose() {
        if (requested >= 0) {
            long r = UNSAFE.getAndSetLong(this, REQUESTED, Long.MIN_VALUE);
            if (r >= 0) {
                onCancelled();
            }
        }
    }
    /**
     * Retrieves the remaining requests and disposes the subscription
     * @return the remaining requests, Long.MIN_VALUE if already disposed
     */
    public final long getAndDispose() {
        long r = requested;
        if (r >= 0) {
            r = UNSAFE.getAndSetLong(this, REQUESTED, Long.MIN_VALUE);
            if (r >= 0) {
                onCancelled();
            }
        }
        return r;
    }
    @Override
    public final void cancel() {
        dispose();
    }
    @Override
    public final void request(long n) {
        if (!Conformance.requestPositive(n, subscriber)) {
            return;
        }
        if (n > 0) {
            for (;;) {
                long r = requested;
                if (r < 0) {
                    return;
                }
                long u = r + n;
                if (u < 0) {
                    u = Long.MAX_VALUE;
                }
                if (UNSAFE.compareAndSwapLong(this, REQUESTED, r, u)) {
                    if (r == 0) {
                        onRequested(u);
                    }
                    return;
                }
            }
        }
    }
    
    @Override
    public final long requested() {
        return requested;
    }
    /**
     * Reduces the current requested amount by the given value and returns the
     * new requested amount.
     * @param n
     * @return the new requested amount or Long.MIN_VALUE indicating a cancelled subscription
     */
    @Override
    public final long produced(long n) {
        if (n < 0) {
            subscriber.onError(new IllegalArgumentException("Negative produced value: " + n));
            dispose();
            return Long.MIN_VALUE;
        }
        for (;;) {
            long r = requested;
            if (n == 0) {
                return r;
            }
            if (r < 0) {
                return Long.MIN_VALUE;
            }
            long u = r - n;
            if (u < 0) {
                subscriber.onError(new IllegalArgumentException("More produced (" + n + " than requested (" + r + ")!"));
                dispose();
                return Long.MIN_VALUE;
            }
            if (UNSAFE.compareAndSwapLong(this, REQUESTED, r, u)) {
                return u;
            }
        }
    }
    @Override
    public final Subscriber<? super T> subscriber() {
        return subscriber;
    }
    /**
     * Called by request() in case the requested counter transitions from 0 to n.
     * @param n
     */
    protected abstract void onRequested(long n);
    /**
     * Called by cancel() once it transitions into the cancelled state.
     */
    protected void onCancelled() {
        
    }
    public static <T> AbstractSubscription<T> create(Subscriber<? super T> subscriber, OnRequested<T> onRequested) {
        return new AbstractSubscription<T>(subscriber) {
            @Override
            protected void onRequested(long n) {
                onRequested.apply(n, this);
            }
        };
    }
    public static <T> AbstractSubscription<T> create(Subscriber<? super T> subscriber, OnRequested<T> onRequested, Runnable onCancelled) {
        return new AbstractSubscription<T>(subscriber) {
            @Override
            protected void onRequested(long n) {
                onRequested.apply(n, this);
            }
            @Override
            protected void onCancelled() {
                onCancelled.run();
            }
        };
    }
    
    public static <T> Subscription createEmpty(Subscriber<? super T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        return new EmptySubscription(subscriber);
    }
    
    /** Subscription that does noting. */
    static final class EmptySubscription implements Subscription {
        final Subscriber<?> subscriber;
        EmptySubscription(Subscriber<?> subscriber) {
            this.subscriber = subscriber;
        }
        @Override
        public void cancel() {
            // NO OP
        }
        @Override
        public void request(long n) {
            Conformance.requestPositive(n, subscriber);
        }
    }
    /**
     * Sets an empty subscription on the subscriber.
     * @param subscriber the target subscriber
     */
    public static void setEmptyOn(Subscriber<?> subscriber) {
        subscriber.onSubscribe(createEmpty(subscriber));
    }
}
