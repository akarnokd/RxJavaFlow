/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rxjf.internal.operators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static rx.internal.operators.BlockingOperatorToFuture.toFuture;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Subscriber;
import rx.exceptions.TestException;

public class BlockingOperatorToFutureTest {

    @Test
    public void testToFuture() throws InterruptedException, ExecutionException {
        Flowable<String> obs = Flowable.just("one");
        Future<String> f = toFuture(obs);
        assertEquals("one", f.get());
    }

    @Test
    public void testToFutureList() throws InterruptedException, ExecutionException {
        Flowable<String> obs = Flowable.just("one", "two", "three");
        Future<List<String>> f = toFuture(obs.toList());
        assertEquals("one", f.get().get(0));
        assertEquals("two", f.get().get(1));
        assertEquals("three", f.get().get(2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExceptionWithMoreThanOneElement() throws Throwable {
        Flowable<String> obs = Flowable.just("one", "two");
        Future<String> f = toFuture(obs);
        try {
            // we expect an exception since there are more than 1 element
            f.get();
        }
        catch(ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testToFutureWithException() {
        Flowable<String> obs = Flowable.create(new OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> observer) {
                observer.onNext("one");
                observer.onError(new TestException());
            }
        });

        Future<String> f = toFuture(obs);
        try {
            f.get();
            fail("expected exception");
        } catch (Throwable e) {
            assertEquals(TestException.class, e.getCause().getClass());
        }
    }

    @Test(expected=CancellationException.class)
    public void testGetAfterCancel() throws Exception {
        Flowable<String> obs = Flowable.create(new OperationNeverComplete<String>());
        Future<String> f = toFuture(obs);
        boolean cancelled = f.cancel(true);
        assertTrue(cancelled);  // because OperationNeverComplete never does
        f.get();                // Future.get() docs require this to throw
    }

    @Test(expected=CancellationException.class)
    public void testGetWithTimeoutAfterCancel() throws Exception {
        Flowable<String> obs = Flowable.create(new OperationNeverComplete<String>());
        Future<String> f = toFuture(obs);
        boolean cancelled = f.cancel(true);
        assertTrue(cancelled);  // because OperationNeverComplete never does
        f.get(Long.MAX_VALUE, TimeUnit.NANOSECONDS);    // Future.get() docs require this to throw
    }

    /**
     * Emits no observations. Used to simulate a long-running asynchronous operation.
     */
    private static class OperationNeverComplete<T> implements Flowable.OnSubscribe<T> {
        @Override
        public void call(Subscriber<? super T> unused) {
            // do nothing
        }
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetWithEmptyFlowable() throws Throwable {
        Flowable<String> obs = Flowable.empty();
        Future<String> f = obs.toBlocking().toFuture();
        try {
            f.get();
        }
        catch(ExecutionException e) {
            throw e.getCause();
        }
    }

    @Test
    public void testGetWithASingleNullItem() throws Exception {
        Flowable<String> obs = Flowable.just((String)null);
        Future<String> f = obs.toBlocking().toFuture();
        assertEquals(null, f.get());
    }
}
