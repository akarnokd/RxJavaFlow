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

package rx.subscribers;

import java.util.*;
import java.util.concurrent.*;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.exceptions.CompositeException;
import rx.internal.Conformance;

/**
 * TODO javadoc
 */
public class TestSubscriber<T> implements Subscriber<T> {
    final List<T> nexts = new ArrayList<>();
    final List<Throwable> errors = new ArrayList<>(); 
    int complete;
    Subscription s;
    final Subscriber<? super T> actual;
    final CountDownLatch latch = new CountDownLatch(1);
    final long initialRequest;
    /**
     * Constructs a new TestSubscriber with no underlying subscriber and
     * an initial request amount of Long.MAX_VALUE.
     */
    public TestSubscriber() {
        this(null, Long.MAX_VALUE);
    }
    /**
     * Constructs a new TestSubscriber with the given subscriber to forward
     * events to and an initial request amount of Long.MAX_VALUE.
     * @param actual the actual subscriber
     */
    public TestSubscriber(Subscriber<? super T> actual) {
        this(actual, Long.MAX_VALUE);
    }
    /**
     * Constructs a new TestSubscriber with no underlying subscriber and
     * an the given initial request amount.
     * @param initialRequest the initial request amount, 0 means no initial request will
     * be made and one must call requestMore to signal the first request.
     */
    public TestSubscriber(long initialRequest) {
        this(null, initialRequest);
    }
    /**
     * Constructs a new TestSubscriber with the given subscriber to forward
     * events to and the given initial request amount.
     * @param actual the actual subscriber or null if none
     * @param initialRequest the initial request amount, 0 means no initial request will
     * be made and one must call requestMore to signal the first request.
     */
    public TestSubscriber(Subscriber<? super T> actual, long initialRequest) {
        this.actual = actual;
        if (initialRequest < 0) {
            throw new IllegalArgumentException("initialRequest >= 0 required");
        }
        this.initialRequest = initialRequest;
    }
    @Override
    public void onSubscribe(Subscription subscription) {
        Conformance.subscriptionNonNull(subscription);
        Subscription curr = s;
        if (!Conformance.onSubscribeOnce(curr, this)) {
            curr.cancel();
            return;
        }
        s = subscription;
        if (initialRequest > 0) {
            subscription.request(initialRequest);
        }
    }
    public final void requestMore(long n) {
        Conformance.subscriptionNonNull(s);
        if (!Conformance.requestPositive(n, this)) {
            return;
        }
        s.request(n);
    }
    public final void cancel() {
        Subscription s2 = s;
        Conformance.subscriptionNonNull(s2);
        s2.cancel();
    }
    
    @Override
    public void onNext(T item) {
        Conformance.itemNonNull(item);
        Conformance.subscriptionNonNull(s);
        nexts.add(item);
        if (actual != null) {
            actual.onNext(item);
        }
    }
    @Override
    public void onError(Throwable throwable) {
        try {
            Conformance.throwableNonNull(throwable);
            Conformance.subscriptionNonNull(s);
            errors.add(throwable);
            if (actual != null) {
                actual.onError(throwable);
            }
        } finally {
            latch.countDown();
        }
    }
    @Override
    public void onComplete() {
        try {
            Conformance.subscriptionNonNull(s);
            complete++;
            if (actual != null) {
                actual.onComplete();
            }
        } finally {
            latch.countDown();
        }
    }
    
    public final Subscription subscription() {
        return s;
    }
    
    // ---------------------------------------------
    // Assertion tests
    // ---------------------------------------------
    
    public final void assertSubscription() {
        if (s == null) {
            throw new AssertionError("No subscription");
        }
    }
    
    public final void assertError() {
        if (errors.isEmpty()) {
            throw new AssertionError("No errors");
        } else
        if (errors.size() > 1) {
            throw new AssertionError("More than one error: " + errors, new CompositeException(errors));
        }
    }
    public final void assertError(Class<? extends Throwable> clazz) {
        if (errors.isEmpty()) {
            throw new AssertionError("No errors");
        } else
        if (errors.size() > 1) {
            throw new AssertionError("More than one error: " + errors, new CompositeException(errors));
        } else 
        if (!clazz.isInstance(errors.get(0))) {
            throw new AssertionError("Expected: " + clazz + ", Actual: " + errors.get(0), errors.get(0));
        }
    }
    
    public final void assertError(Throwable throwable) {
        if (errors.isEmpty()) {
            throw new AssertionError("No errors");
        } else
        if (errors.size() > 1) {
            throw new AssertionError("More than one error: " + errors, new CompositeException(errors));
        } else 
        if (!Objects.equals(throwable, errors.get(0))) {
            throw new AssertionError("Expected: " + throwable + ", Actual: " + errors.get(0), errors.get(0));
        }
    }
    
    public final void assertNoErrors() {
        if (errors.size() == 1) {
            throw new AssertionError("One error present: " + errors.get(0), errors.get(0));
        } else
        if (errors.size() > 1) {
            throw new AssertionError("Multiple errors present: " + errors, new CompositeException(errors));
        }
    }
    
    /**
     * Assert if either a single onComplete or onError happened.
     * @throws AssertionError if either no terminal events received, multiple errors
     * or multiple completion events received
     */
    public final void assertTerminalEvent() {
        if (errors.isEmpty() && complete == 0) {
            throw new AssertionError("No terminal event(s)");
        }
        if (errors.size() > 1) {
            throw new AssertionError("Multiple errors: " + errors, new CompositeException(errors));
        }
        if (complete > 1) {
            throw new AssertionError("Multiple completion events: " + complete);
        }
    }
    public final void assertNoTerminalEvent() {
        if (!errors.isEmpty() || complete != 0) {
            throw new AssertionError("Terminal event(s) present: " + errors + ", complete: " + complete, 
                    errors.size() == 1 ? errors.get(0) : new CompositeException(errors));
        }
    }
    public final void assertComplete() {
        if (complete == 0) {
            throw new AssertionError("No completion event");
        } else
        if (complete > 1) {
            throw new AssertionError("Multiple completion events: " + complete);
        }
    }
    
    public final void assertNoComplete() {
        if (complete == 1) {
            throw new AssertionError("Completion event presents");
        } else
        if (complete > 1) {
            throw new AssertionError("Multiple completion events: " + complete);
        }
    }
    
    public final void assertValues(Iterable<? extends T> values) {
        Iterator<? extends T> it = values.iterator();
        Iterator<? extends T> nt = nexts.iterator();
        int n = 0;
        for (;;) {
            boolean itn = it.hasNext();
            boolean ntn = nt.hasNext();
            if (!itn || !ntn) {
                break;
            }
            T e = it.next();
            T a = nt.next();
            if (!Objects.equals(e, a)) {
                throw new AssertionError("Value mismatch @ " + n + ", Expected: " + e + ", Actual: " + a);
            }
            n++;
        }
        if (n == nexts.size() && it.hasNext()) {
            throw new AssertionError("Too few elements: " + nexts);
        } else
        if (n < nexts.size()) {
            throw new AssertionError("Too many elements: " + n + " expected");
        }
    }
    public final void assertNoValues() {
        if (!nexts.isEmpty()) {
            throw new AssertionError("Values present: " + nexts);
        }
    }
    
    public final void assertValueCount(int count) {
        if (nexts.size() != count) {
            throw new AssertionError("Different number of values. Expected: " + count + ", Actual: " + nexts.size() + ", " + nexts);
        }
    }
    
    public final void assertFirst(T value) {
        if (nexts.isEmpty()) {
            throw new AssertionError("No values");
        }
        if (!Objects.equals(value, nexts.get(0))) {
            throw new AssertionError("First differs. Expected: " + value + ", Actual: " +nexts.get(0));
        }
    }
    public final void assertLast(T value) {
        if (nexts.isEmpty()) {
            throw new AssertionError("No values");
        }
        if (!Objects.equals(value, nexts.get(nexts.size() - 1))) {
            throw new AssertionError("Last differs. Expected: " + value + ", Actual: " +nexts.get(0));
        }
    }
    
    @SafeVarargs
    public final void assertValues(T... values) {
        if (!Arrays.asList(values).equals(nexts)) {
            throw new AssertionError("Values differ, Expected: " + Arrays.toString(values) + ", Actual: " + nexts);
        }
    }
    public final void awaitTerminalEvent() {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    public final void awaitTerminalEvent(long time, TimeUnit unit) {
        try {
            if (!latch.await(time, unit)) {
                throw new RuntimeException("Timeout");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    /**
     * Awaits the terminal events and returns false if timeout happens.
     * @param time
     * @param unit
     * @return false if timeout happened
     */
    public final boolean await(long time, TimeUnit unit) {
        try {
            return latch.await(time, unit);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    public final List<Throwable> getErrors() {
        return errors;
    }
    public final List<T> getValues() {
        return nexts;
    }
    public final int getCompletions() {
        return complete;
    }
    /**
     * @deprecated use {@link #getValues()} instead
     */
    @Deprecated
    public final List<T> getOnNextEvents() {
        return nexts;
    }
    /**
     * @deprecated use {@link #getErrors()} instead
     */
    @Deprecated
    public final List<Throwable> getOnErrorEvents() {
        return errors;
    }
}
