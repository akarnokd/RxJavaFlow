/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rxjf.internal.operators;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.mockito.InOrder;
import static org.mockito.Mockito.*;
import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.exceptions.TestException;
import rx.functions.Action1;
import rx.functions.BiFunction;
import rx.subjects.PublishSubject;

public class OperatorRetryWithPredicateTest {
    BiFunction<Integer, Throwable, Boolean> retryTwice = new BiFunction<Integer, Throwable, Boolean>() {
        @Override
        public Boolean call(Integer t1, Throwable t2) {
            return t1 <= 2;
        }
    };
    BiFunction<Integer, Throwable, Boolean> retry5 = new BiFunction<Integer, Throwable, Boolean>() {
        @Override
        public Boolean call(Integer t1, Throwable t2) {
            return t1 <= 5;
        }
    };
    BiFunction<Integer, Throwable, Boolean> retryOnTestException = new BiFunction<Integer, Throwable, Boolean>() {
        @Override
        public Boolean call(Integer t1, Throwable t2) {
            return t2 instanceof IOException;
        }
    };
    @Test
    public void testWithNothingToRetry() {
        Flowable<Integer> source = Flowable.range(0, 3);
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        source.retry(retryTwice).subscribe(o);
        
        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    @Test
    public void testRetryTwice() {
        Flowable<Integer> source = Flowable.create(new OnSubscribe<Integer>() {
            int count;
            @Override
            public void call(Subscriber<? super Integer> t1) {
                count++;
                t1.onNext(0);
                t1.onNext(1);
                if (count == 1) {
                    t1.onError(new TestException());
                    return;
                }
                t1.onNext(2);
                t1.onNext(3);
                t1.onComplete();
            }
        });
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        source.retry(retryTwice).subscribe(o);

        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o).onNext(3);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
        
    }
    @Test
    public void testRetryTwiceAndGiveUp() {
        Flowable<Integer> source = Flowable.create(new OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> t1) {
                t1.onNext(0);
                t1.onNext(1);
                t1.onError(new TestException());
            }
        });
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        source.retry(retryTwice).subscribe(o);

        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
        
    }
    @Test
    public void testRetryOnSpecificException() {
        Flowable<Integer> source = Flowable.create(new OnSubscribe<Integer>() {
            int count;
            @Override
            public void call(Subscriber<? super Integer> t1) {
                count++;
                t1.onNext(0);
                t1.onNext(1);
                if (count == 1) {
                    t1.onError(new IOException());
                    return;
                }
                t1.onNext(2);
                t1.onNext(3);
                t1.onComplete();
            }
        });
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        source.retry(retryOnTestException).subscribe(o);

        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o).onNext(3);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    @Test
    public void testRetryOnSpecificExceptionAndNotOther() {
        final IOException ioe = new IOException();
        final TestException te = new TestException();
        Flowable<Integer> source = Flowable.create(new OnSubscribe<Integer>() {
            int count;
            @Override
            public void call(Subscriber<? super Integer> t1) {
                count++;
                t1.onNext(0);
                t1.onNext(1);
                if (count == 1) {
                    t1.onError(ioe);
                    return;
                }
                t1.onNext(2);
                t1.onNext(3);
                t1.onError(te);
            }
        });
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        source.retry(retryOnTestException).subscribe(o);

        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(0);
        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onNext(2);
        inOrder.verify(o).onNext(3);
        inOrder.verify(o).onError(te);
        verify(o, never()).onError(ioe);
        verify(o, never()).onComplete();
    }
    
    @Test
    public void testUnsubscribeFromRetry() {
        PublishSubject<Integer> subject = PublishSubject.create();
        final AtomicInteger count = new AtomicInteger(0);
        Subscription sub = subject.retry(retryTwice).subscribe(new Action1<Integer>() {
            @Override
            public void call(Integer n) {
                count.incrementAndGet();
            }
        });
        subject.onNext(1);
        sub.unsubscribe();
        subject.onNext(2);
        assertEquals(1, count.get());
    }
    
    @Test(timeout = 10000)
    public void testUnsubscribeAfterError() {

        @SuppressWarnings("unchecked")
        Observer<Long> observer = mock(Observer.class);

        // Flowable that always fails after 100ms
        OperatorRetryTest.SlowFlowable so = new OperatorRetryTest.SlowFlowable(100, 0);
        Flowable<Long> o = Flowable
                .create(so)
                .retry(retry5);

        OperatorRetryTest.AsyncObserver<Long> async = new OperatorRetryTest.AsyncObserver<Long>(observer);

        o.subscribe(async);

        async.await();

        InOrder inOrder = inOrder(observer);
        // Should fail once
        inOrder.verify(observer, times(1)).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onComplete();

        assertEquals("Start 6 threads, retry 5 then fail on 6", 6, so.efforts.get());
        assertEquals("Only 1 active subscription", 1, so.maxActive.get());
    }

    @Test(timeout = 10000)
    public void testTimeoutWithRetry() {

        @SuppressWarnings("unchecked")
        Observer<Long> observer = mock(Observer.class);

        // Flowable that sends every 100ms (timeout fails instead)
        OperatorRetryTest.SlowFlowable so = new OperatorRetryTest.SlowFlowable(100, 10);
        Flowable<Long> o = Flowable
                .create(so)
                .timeout(80, TimeUnit.MILLISECONDS)
                .retry(retry5);

        OperatorRetryTest.AsyncObserver<Long> async = new OperatorRetryTest.AsyncObserver<Long>(observer);

        o.subscribe(async);

        async.await();

        InOrder inOrder = inOrder(observer);
        // Should fail once
        inOrder.verify(observer, times(1)).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onComplete();

        assertEquals("Start 6 threads, retry 5 then fail on 6", 6, so.efforts.get());
    }
}
