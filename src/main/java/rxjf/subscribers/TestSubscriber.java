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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

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
        Assert.assertFalse(errors.isEmpty());
    }
    public final void assertError(Class<? extends Throwable> clazz) {
        if (errors.isEmpty()) {
            Assert.fail("No errors");
        } else
        if (errors.size() > 1) {
            Assert.fail("More than one error: " + errors);
        } else {
            Assert.assertTrue("Expected: " + clazz + ", Actual: " + errors.get(0), clazz.isInstance(errors.get(0)));
        }
    }
    
    public final void assertNoError() {
        Assert.assertTrue(errors.isEmpty());
    }
    
    public final void assertTerminalEvent() {
        Assert.assertTrue("No terminal event(s).", !errors.isEmpty() || complete != 0);
    }
    public final void assertNoTerminalEvent() {
        Assert.assertTrue("Terminal event present!", errors.isEmpty() && complete == 0);
    }
    public final void assertComplete() {
        Assert.assertEquals("No completion event", 1, complete);
    }
    public final void assertNoComplete() {
        Assert.assertEquals("One or more completion events", 0, complete);
    }
    public final void assertValues(Iterable<? extends T> values) {
        Iterator<? extends T> it = values.iterator();
        Iterator<? extends T> nt = nexts.iterator();
        int n = 0;
        while (it.hasNext() == nt.hasNext()) {
            Assert.assertEquals(it.next(), nt.next());
            n++;
        }
        if (n == nexts.size() && it.hasNext()) {
            Assert.fail("Too few elements: " + nexts);
        } else
        if (n < nexts.size()) {
            Assert.fail("Too many elements: " + n + " expected");
        }
    }
    public final void assertNoValues() {
        Assert.assertEquals(0, nexts.size());
    }
    @SafeVarargs
    public final void assertValues(T... values) {
        Assert.assertEquals(Arrays.asList(values), nexts);
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
