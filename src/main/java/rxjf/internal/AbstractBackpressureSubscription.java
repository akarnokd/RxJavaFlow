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

import static rxjf.internal.UnsafeAccess.*;
import rxjf.Flow.Subscriber;
import rxjf.exceptions.MissingBackpressureException;

/**
 * A subscription, that coordinates value production and
 * value emission for a subscriber while respecting the subscriber's requests.
 */
public abstract class AbstractBackpressureSubscription<T> extends AbstractSubscription<T> {
    /** 
     * Flag indicating a received onError event should cut ahead of any queued normal events.
     * TODO decide its default value or default behavior 
     */
    protected static final boolean ERROR_CUTS_AHEAD = Boolean.getBoolean("rxjf.internal.queue-subscription.error-cuts-ahead");
    /** Stores the terminal event object as returned by the NotificationLite. */
    volatile Object terminal;
    /** Atomic field accessor. */
    static final long TERMINAL = addressOf(AbstractBackpressureSubscription.class, "terminal");
    /** Counter indicating the number of requests made to drain the queue. */
    volatile int emitting;
    /** Atomic field accessor. */
    static final long EMITTING = addressOf(AbstractBackpressureSubscription.class, "emitting");
    /** Helps converting events to objects and back. */
    protected final NotificationLite<T> nl = NotificationLite.instance();
    /**
     * Constructs a QueueSubscription with the given subscriber and an SpscArrayQueue 
     * with capacity Flow.defaultBufferSize().
     * @param subscriber the subscriber to emit values to
     */
    public AbstractBackpressureSubscription(Subscriber<? super T> subscriber) {
        super(subscriber);
    }
    
    /**
     * Implement this to handle the offering of a single value.
     * @param value the value to offer, never null
     * @return true if the value was accepted, false if the value was rejected
     */
    protected abstract boolean offer(T value);
    
    /**
     * Returns the available value or null if none.
     * @return the available value or null if none
     */
    protected abstract T peek();
    
    /**
     * Returns and consumes an available value or null if none.
     * @return and consumes an available value or null if none
     */
    protected abstract T poll();
    
    @Override
    protected final void onRequested(long n) {
        drain();
    }
    /**
     * TODO
     * <p>
     * Methods offer and terminate should be called non-concurrently.
     * @param value the value to queue or emit
     * @return true if the value could be queued, false if the subscription has
     * already received a terminal event or the underlying queue rejected the value.
     */
    public final void onNext(T value) {
        Conformance.itemNonNull(value);
        if (terminal != null) {
            return;
        }
        if (offer(value)) {
            drain();
        } else {
            onError(new MissingBackpressureException());
        }
    }
    /**
     * TODO
     * <p>
     * Methods offer and terminate should be called non-concurrently.
     */
    public final void onComplete() {
        if (terminal != null) {
            return;
        }
        UNSAFE.putOrderedObject(this, TERMINAL, nl.complete());
        drain();
    }
    /**
     * TODO
     * <p>
     * Methods offer and terminate should be called non-concurrently.
     */
    public final void onError(Throwable t) {
        if (terminal != null) {
            return;
        }
        UNSAFE.putOrderedObject(this, TERMINAL, nl.error(t));
        drain();
    }
    
    /**
     * Drains the contents of the queue respecting the requested amount.
     * <p>
     * Can be called any time because it serializes the drain process.
     */
    public final void drain() {
        if (UNSAFE.getAndAddInt(this, EMITTING, 1) == 0) {
            Subscriber<? super T> subscriber = subscriber();
            do {
                long r = requested();
                long c = 0;
                for (;;) {
                    if (isDisposed()) {
                        return;
                    }
                    T value = peek();
                    if (value == null) {
                        Object term = terminal;
                        if (term != null) {
                            nl.accept(subscriber, term);
                            dispose();
                            return;
                        }
                    } else
                    if (ERROR_CUTS_AHEAD) {
                        Object term = terminal;
                        if (nl.isError(term)) {
                            subscriber.onError(nl.getError(term));
                            return;
                        }
                    }
                    if (r == 0 || value == null) {
                        if (c > 0) {
                            produced(c);
                        }
                        break;
                    }
                    poll();
                    subscriber.onNext(value);
                    c++;
                    r--;
                }
            } while (UNSAFE.getAndAddInt(this, EMITTING, -1) > 1);
        }
    }

}
