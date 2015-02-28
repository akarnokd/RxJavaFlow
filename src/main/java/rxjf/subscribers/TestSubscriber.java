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

package rxjf.subscribers;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;

/**
 * 
 */
public class TestSubscriber<T> implements Subscriber<T> {
    final List<T> nexts = new ArrayList<>();
    final List<Throwable> errors = new ArrayList<>(); 
    int complete;
    Subscription s;
    final Subscriber<? super T> actual;
    final CountDownLatch latch = new CountDownLatch(1);
    public TestSubscriber() {
        actual = null;
    }
    public TestSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    @Override
    public void onSubscribe(Subscription subscription) {
        s = subscription;
    }
    public void request(long n) {
        s.request(n);
    }
    @Override
    public void onNext(T item) {
        nexts.add(item);
        if (actual != null) {
            actual.onNext(item);
        }
    }
    @Override
    public void onError(Throwable throwable) {
        try {
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
            complete++;
            if (actual != null) {
                actual.onComplete();
            }
        } finally {
            latch.countDown();
        }
    }
    
    public final void assertError() {
        if (errors.isEmpty()) {
            throw new AssertionError("No errors");
        }
    }
    public final void assertError(Class<? extends Throwable> clazz) {
        if (errors.isEmpty()) {
            throw new AssertionError("No errors");
        } else
        if (errors.size() > 1) {
            throw new AssertionError("More than one error: " + errors);
        } else 
        if (!clazz.isInstance(errors.get(0))) {
            
            throw new AssertionError("Expected: " + clazz + ", Actual: " + errors.get(0));
        }
    }
    
    public final void assertNoError() {
        if (!errors.isEmpty()) {
            throw new AssertionError("One or more errors: " + errors);
        }
    }
    
    public final void assertTerminalEvent() {
        if (errors.isEmpty() && complete == 0) {
            throw new AssertionError("No terminal event(s)");
        }
    }
    public final void assertNoTerminalEvent() {
        if (!errors.isEmpty() || complete != 0) {
            throw new AssertionError("Terminal event present: " + errors + ", complete: " + complete);
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
        while (it.hasNext() == nt.hasNext()) {
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
            throw new AssertionError("Values present: " + nexts.size());
        }
    }
    @SafeVarargs
    public final void assertValues(T... values) {
        if (!Arrays.asList(values).equals(nexts)) {
            throw new AssertionError("Values differ, Expected: " + values + ", Actual: " + nexts);
        }
    }
    public final void await() {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
    public final void await(long time, TimeUnit unit) {
        try {
            if (!latch.await(time, unit)) {
                throw new RuntimeException("Timeout");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }
}
