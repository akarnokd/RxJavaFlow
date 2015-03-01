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

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.Conformance;

/**
 * A Subscription that will deliver a single value (received asynchronously)
 * to an underlying Subscriber on its request.
 * <p>
 * at implNote
 * All method implementations are wait-free on platforms with atomic exchange.
 */
public final class ScalarBackpressureSubscription<T> implements Subscription {
    final Subscriber<? super T> subscriber;
    volatile Object value;
    static final long VALUE = addressOf(ScalarBackpressureSubscription.class, "value");
    
    /** Indicates a request has been made but no value has been emitted via onNext. */
    static final Object REQUESTED = new Serializable() {
        /** */
        private static final long serialVersionUID = -5798566283622677717L;

        @Override
        public String toString() {
            return "REQUESTED";
        }
    };
    /** Indicates a value has been consumed or the subscription has been terminated. */
    static final Object TERMINATED = new Serializable() {
        /** */
        private static final long serialVersionUID = 4385997167317601013L;

        @Override
        public String toString() {
            return "TERMINATED";
        }
    };
    
    /**
     * Constructs a scalar backpressure-subscription with the given subscriber
     * as the client
     * @param subscriber the subscriber to deliver the events to
     */
    public ScalarBackpressureSubscription(Subscriber<? super T> subscriber) {
        this.subscriber = Conformance.subscriberNonNull(subscriber);
    }
    @Override
    public void cancel() {
        if (value != TERMINATED) {
            UNSAFE.getAndSetObject(this, VALUE, TERMINATED);
        }
    }
    @Override
    public void request(long n) {
        if (!Conformance.requestPositive(n, subscriber)) {
            cancel();
            return;
        }
        Object v = value;
        // have we already requested or terminated -> quit
        if (v == REQUESTED || v == TERMINATED) {
            return;
        }

        // there is a value available
        if (v != null) {
            // swap in terminal state
            deliver(v);
            return;
        }

        // otherwise, signal requested
        if (UNSAFE.compareAndSwapObject(this, VALUE, null, REQUESTED)) {
            return;
        }

        // failed, somebody else has changed the state
        // since we never change it to null, v should be non-null
        v = value;
        // have we already requested or terminated -> quit
        if (v == REQUESTED || v == TERMINATED) {
            return;
        }
        deliver(v);
    }
    /**
     * Set a single item to be delivered to the subscriber on request.
     * <p>
     * If a value has already been set, an IllegalArgumentException is sent
     * to the subscriber.
     * @param item the item, MUST NOT be null
     */
    public void onNext(T item) {
        Conformance.itemNonNull(item);

        // failed, check the reason
        Object v = this.value;
        // terminated -> quit
        if (v == TERMINATED) {
            return;
        }
        // subscriber just requested -> deliver
        if (v == REQUESTED) {
            deliver(item);
            return;
        }
        // if value already set -> report error
        if (v != null) {
            onError(new IllegalArgumentException("Value already set. Current: " + v + ", New: " + item));
            return;
        }

        // attempt to swap in the item
        if (UNSAFE.compareAndSwapObject(this, VALUE, null, item)) {
            return;
        }
        
        // the swap failed, the state has been changed
        // since we never swap in null, v should be non-null here
        v = this.value;
        // terminated -> quit
        if (v == TERMINATED) {
            return;
        }
        // subscriber just requested -> deliver
        if (v == REQUESTED) {
            deliver(item);
            return;
        }
        // value already set -> report error
        onError(new IllegalArgumentException("Value already set. Current: " + v + ", New: " + item));
    }
    /**
     * Atomically delivers the given value and
     * swaps in the terminal state.
     * @param v the value to deliver
     */
    private void deliver(Object v) {
        Object vnow = UNSAFE.getAndSetObject(this, VALUE, TERMINATED);
        // if not already terminated, emit the value we got
        if (vnow != TERMINATED) {
            @SuppressWarnings("unchecked")
            T v2 = (T)v;
            subscriber.onNext(v2);
            subscriber.onComplete();
        }
    }
    /**
     * Reports an error, overwriting any previous value if not already
     * terminated.
     * @param throwable the error
     */
    public void onError(Throwable throwable) {
        Conformance.throwableNonNull(throwable);
        Object v = value;
        if (v != TERMINATED) {
            Object old = UNSAFE.getAndSetObject(this, VALUE, TERMINATED);
            if (old != TERMINATED) {
                subscriber.onError(throwable);
            }
        }
    }
    /**
     * Reports an completion event only if no value has been set yet via onNext.
     */
    public void onComplete() {
        Object v = value;
        if (v == null || v == REQUESTED) {
            if (UNSAFE.compareAndSwapObject(this, VALUE, v, TERMINATED)) {
                subscriber.onComplete();
            }
        }
    }
}
