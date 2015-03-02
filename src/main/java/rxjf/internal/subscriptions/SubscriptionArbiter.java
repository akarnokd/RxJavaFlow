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

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.disposables.Disposable;
import rxjf.internal.Conformance;

/**
 * Allows replacing an underlying subscription while keeping
 * track of all undelivered requests and re-requesting the amount
 * from the new subscription while serializing access to the underlying subscription.
 * <p>
 * This Subscription serializes all actions of its public methods and thus is thread-safe.
 * <p>
 * By relaying values through this {@link Subscription}, there is no need for the
 * subscriber to be {@link rxjf.subscribers.SerializedSubscriber SerializedSubscriber} (and in fact, reduces performance due to double-serializing).
 */
public final class SubscriptionArbiter<T> implements Subscription, Disposable {
    /** The actual subscriber. */
    final Subscriber<? super T> subscriber;
    /** Guarded by this. */
    boolean emitting;
    /** Guarded by this. */
    List<Object> queue;
    /** Accessed while emitting == true. */
    Subscription current;
    /** Written while emitting == true. */
    volatile long requested;
    /** Address of field {@link #requested}. */
    static final long REQUESTED = addressOf(SubscriptionArbiter.class, "requested");
    
    /**
     * Constructs a SubscriptionArbiter with the given subscriber as the output target.
     * <p>
     * Note: does not associate itself with the subscriber via {@link Subscriber#onSubscribe(Subscription)},
     * callers should do this manually after the constructor returns.
     * @param subscriber the subscriber to use as the output target
     */
    public SubscriptionArbiter(Subscriber<? super T> subscriber) {
        this.subscriber = Conformance.subscriberNonNull(subscriber);
    }
    @Override
    public void request(long n) {
        if (!Conformance.requestPositive(n, subscriber)) {
            cancel();
            return;
        }
        handle(n, this::doRequest, RequestWrapper::new, false);
    }
    @Override
    public void cancel() {
        handle(null, (o, r) -> doCancel(r), e -> CANCEL, true);
    }
    
    /**
     * Sets a new non-null subscription and re-requests any undelivered requests
     * from this new subscription.
     * @param newSubscription
     */
    public void set(Subscription newSubscription) {
        Conformance.subscriptionNonNull(newSubscription);
        handle(newSubscription, this::doSubscription, SubscriptionWrapper::new, false);
    }
    
    /**
     * Deliver an onNext event.
     * @param item
     */
    public void onNext(T item) {
        Conformance.itemNonNull(item);
        handle(item, this::doNext, e -> e, false);
    }
    
    /**
     * Deliver an onError event.
     * <p>Cuts ahead of any queued operations.
     * @param t
     */
    public void onError(Throwable t) {
        Conformance.throwableNonNull(t);
        handle(t, (v, r) -> doError(v, r), ErrorWrapper::new, true);
    }
    /**
     * Deliver an onComplete event.
     */
    public void onComplete() {
        handle(null, (v, r) -> doComplete(r), e -> COMPLETE, false);
    }
    @Override
    public void dispose() {
        cancel();
    }
    @Override
    public boolean isDisposed() {
        return requested == Long.MIN_VALUE;
    }
    
    /** The current requested amount. */
    public long requested() {
        return requested;
    }
    
    /**
     * The common serializing loop with callbacks for different states.
     * @param value the direct value to deliver
     * @param onFirst called when entered into the emitter loop, receives the direct value, the
     * current requested amount and should return a new requested amount
     * @param onQueue called when someone else is emitting to convert the direct 
     * value into a queued value (wrapping if necessary)
     * @param skipAhead if {@code true} the queued value, returned by onQueue, will skip ahead
     *  of other queued values; can be used to give priority to error reporting and cancellation
     */
    <U> void handle(U value, ObjectLongFunction<? super U> onFirst, 
            Function<? super U, Object> onQueue, boolean skipAhead) {
        synchronized (this) {
            if (emitting) {
                List<Object> list = queue;
                if (list == null) {
                    list = new ArrayList<>(4);
                    queue = list;
                }
                Object q = onQueue.apply(value);
                if (skipAhead) {
                    list.add(0, q);
                } else {
                    list.add(q);
                }
                return;
            }
            emitting = true;
        }
        boolean skipFinal = false;
        try {
            long r = requested;
            r = onFirst.apply(value, r);
            for (;;) {
                List<Object> list;
                synchronized (this) {
                    list = queue;
                    queue = null; // clear?
                    if (list == null) {
                        skipFinal = true;
                        emitting = false;
                        return;
                    }
                }
                for (Object o : list) {
                    r = accept(o, r);
                }
            }
        } finally {
            if (!skipFinal) {
                synchronized (this) {
                    emitting = false;
                }
            }
        }
    }
    
    long accept(Object value, long requested) {
        if (value instanceof SubscriptionWrapper) {
            Subscription ns = ((SubscriptionWrapper)value).s;
            return doSubscription(ns, requested);
        } else
        if (value == COMPLETE) {
            return doComplete(requested);
        } else
        if (value == CANCEL) {
            return doCancel(requested);
        } else
        if (value instanceof ErrorWrapper) {
            return doError(((ErrorWrapper)value).error, requested);
        } else
        if (value instanceof RequestWrapper) {
            long n = ((RequestWrapper)value).requested;
            return doRequest(requested, n);
        }
        return doNext(value, requested);
    }
    long doRequest(long n, long requested) {
        if (requested >= 0) {
            long u = n + requested;
            if (u < 0) {
                u = Long.MAX_VALUE;
            }
            soRequested(u);
            if (current != null) {
                current.request(n);
            }
            return u;
        }
        return requested;
    }
    long doNext(Object value, long requested) {
        Conformance.subscriptionNonNull(current);
        if (requested == 0) {
            Conformance.mustRequestFirst(subscriber);
            soRequested(Long.MIN_VALUE);
            return Long.MIN_VALUE;
        }
        if (requested > 0) {
            @SuppressWarnings("unchecked")
            T value2 = (T)value;
            subscriber.onNext(value2);
            
            long newRequested = requested - 1;
            soRequested(newRequested);
            
            return newRequested;
        }
        return requested;
    }
    long doError(Throwable value, long requested) {
        Conformance.subscriptionNonNull(current);
        if (requested >= 0) {
            subscriber.onError(value);
            soRequested(Long.MIN_VALUE);
            if (current != null) {
                current.cancel();
            }
            return Long.MIN_VALUE;
        }
        return requested;
    }
    long doComplete(long requested) {
        Conformance.subscriptionNonNull(current);
        if (requested >= 0) {
            subscriber.onComplete();
            soRequested(Long.MIN_VALUE);
            if (current != null) {
                current.cancel();
            }
            return Long.MIN_VALUE;
        }
        return requested;
    }
    long doSubscription(Subscription ns, long requested) {
        if (requested < 0) {
            ns.cancel();
        } else {
            if (current != null) {
                current.cancel();
            }
            current = ns;
            if (requested > 0) {
                ns.request(requested);
            }
        }
        return requested;
    }
    long doCancel(long requested) {
        if (requested >= 0) {
            if (current != null) {
                current.cancel();
            }
            soRequested(Long.MIN_VALUE);
        }
        return Long.MIN_VALUE;
    }
    
    void soRequested(long value) {
        UNSAFE.putOrderedLong(this, REQUESTED, value);
    }

    /** Cancellation token. */
    static final Object CANCEL = new Serializable() {
        /** */
        private static final long serialVersionUID = -4002034338989159822L;

        @Override
        public String toString() {
            return "CANCEL";
        }
    };
    /** A new request wrapper. */
    static final class RequestWrapper implements Serializable {
        /** */
        private static final long serialVersionUID = 134840311869321172L;
        final long requested;
        RequestWrapper(long requested) {
            this.requested = requested;
        }
        @Override
        public String toString() {
            return "Request: " + requested;
        }
    }
    /** A new subscription wrapper. */
    static final class SubscriptionWrapper implements Serializable {
        /** */
        private static final long serialVersionUID = 3849337445658301974L;
        final Subscription s;
        SubscriptionWrapper(Subscription s) {
            this.s = s;
        }
        @Override
        public String toString() {
            return "Subscription";
        }
    }
    
    /** An error wrapper. */
    static final class ErrorWrapper implements Serializable  {
        /** */
        private static final long serialVersionUID = 1283700143864356467L;
        final Throwable error;
        ErrorWrapper(Throwable error) {
            this.error = error;
        }
        @Override
        public String toString() {
            return "Error: " + error;
        }
    }
    
    /** An onComplete indicator token. */
    static final Object COMPLETE = new Serializable() {
        /** */
        private static final long serialVersionUID = 5009550202959465730L;

        @Override
        public String toString() {
           return "Complete";
        }
    };
    
    /**
     * Functional interface that accepts an object of T, a long and returns a long.
     *
     * @param <T> the object's type
     */
    @FunctionalInterface
    interface ObjectLongFunction<T> {
        long apply(T value, long n);
    }
}
