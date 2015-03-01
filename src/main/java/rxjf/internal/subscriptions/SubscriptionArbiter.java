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
import java.util.function.*;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.Conformance;

/**
 * Allows replacing an underlying subscription while keeping
 * track of all undelivered requests and re-requesting the amount
 * from the new subscription.
 */
public final class SubscriptionArbiter<T> implements Subscription {
    final Subscriber<? super T> subscriber;
    /** Guarded by this. */
    boolean emitting;
    /** Guarded by this. */
    List<Object> queue;
    /** Accessed while emitting == true. */
    Subscription current;
    /** Written while emitting == true. */
    volatile long requested;
    static final long REQUESTED = addressOf(SubscriptionArbiter.class, "requested");
    
    public SubscriptionArbiter(Subscriber<? super T> subscriber) {
        this.subscriber = Conformance.subscriberNonNull(subscriber);
    }
    @Override
    public void request(long n) {
        Conformance.requestPositive(n, subscriber);
        handle(n, e -> {
            long r = requested;
            long ne = e;
            long u = r + ne;
            if (u < ne) {
                u = Long.MAX_VALUE;
            }
            soRequested(u);
            if (current != null) {
                current.request(u);
            }
            return true;
        }, Request::new, false);
    }
    @Override
    public void cancel() {
        handle(null, e -> {
            if (current != null) {
                current.cancel();
            }
            return false;
        }, e -> CANCEL, true);
    }
    
    public void set(Subscription newSubscription) {
        Conformance.subscriptionNonNull(newSubscription);
        
        handle(newSubscription, e -> {
            if (current != null) {
                current.cancel();
            }
            current = e;
            long r = requested;
            if (e != null) {
                current.request(r);
            }
            return true;
        }, NewSubscription::new, false);
    }
    
    public void onNext(T item) {
        Conformance.itemNonNull(item);
        handle(item, e -> {
            subscriber.onNext(e);
            return true;
        }, e -> e, false);
    }
    
    <U> void handle(U value, Predicate<? super U> onFirst, 
            Function<? super U, Object> onQueue, boolean cutAhead) {
        if (requested < 0) {
            return;
        }
        synchronized (this) {
            if (requested < 0) {
                return;
            }
            if (emitting) {
                if (queue == null) {
                    queue = new ArrayList<>(4);
                }
                if (cutAhead) {
                    queue.clear();
                }
                queue.add(onQueue.apply(value));
                return;
            }
            emitting = true;
        }
        if (!onFirst.test(value)) {
            return;
        }
        
        for (;;) {
            List<Object> list;
            synchronized (this) {
                list = queue;
                queue = null; // clear?
                if (list == null) {
                    emitting = false;
                    return;
                }
            }
            for (Object o : list) {
                long r = requested;
                if (r < 0) {
                    return; // cancelled
                }
                if (o instanceof Request) {
                    long n = ((Request)o).r;
                    long u = r + n;
                    if (u < n) {
                        u = Long.MAX_VALUE;
                    }
                    soRequested(u);
                    if (current != null) {
                        current.request(u);
                    }
                } else
                // FIXME use local class
                if (o instanceof NewSubscription) {
                    if (current != null) {
                        current.cancel();
                    }
                    current = ((NewSubscription)o).s;
                    if (r > 0) {
                        current.request(r);
                    }
                } else
                if (o == CANCEL) {
                    if (current != null) {
                        current.cancel();
                    }
                    soRequested(Long.MIN_VALUE);
                    return;
                } else
                if (o != null) {
                    @SuppressWarnings("unchecked")
                    T o2 = (T)o;
                    subscriber.onNext(o2);
                    soRequested(r - 1);
                }
            }
        }
    }
    void soRequested(long value) {
        UNSAFE.putOrderedLong(this, REQUESTED, value);
    }

    static final Object CANCEL = new Serializable() {
        /** */
        private static final long serialVersionUID = -4002034338989159822L;

        @Override
        public String toString() {
            return "CANCEL";
        }
    };
    static final class Request implements Serializable {
        /** */
        private static final long serialVersionUID = 134840311869321172L;
        final long r;
        Request(long r) {
            this.r = r;
        }
        @Override
        public String toString() {
            return "Request: " + r;
        }
    }
    static final class NewSubscription implements Serializable {
        /** */
        private static final long serialVersionUID = 3849337445658301974L;
        final Subscription s;
        NewSubscription(Subscription s) {
            this.s = s;
        }
        @Override
        public String toString() {
            return "Subscription";
        }
    }
}
