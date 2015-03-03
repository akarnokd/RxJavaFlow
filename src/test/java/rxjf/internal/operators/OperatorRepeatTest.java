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
package rx.internal.operators;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.exceptions.TestException;
import rx.functions.Function;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class OperatorRepeatTest {

    @Test(timeout = 2000)
    public void testRepetition() {
        int NUM = 10;
        final AtomicInteger count = new AtomicInteger();
        int value = Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(final Subscriber<? super Integer> o) {
                o.onNext(count.incrementAndGet());
                o.onComplete();
            }
        }).repeat(Schedulers.computation()).take(NUM).toBlocking().last();

        assertEquals(NUM, value);
    }

    @Test(timeout = 2000)
    public void testRepeatTake() {
        Flowable<Integer> xs = Flowable.just(1, 2);
        Object[] ys = xs.repeat(Schedulers.newThread()).take(4).toList().toBlocking().last().toArray();
        assertArrayEquals(new Object[] { 1, 2, 1, 2 }, ys);
    }

    @Test(timeout = 20000)
    public void testNoStackOverFlow() {
        Flowable.just(1).repeat(Schedulers.newThread()).take(100000).toBlocking().last();
    }

    @Test
    public void testRepeatTakeWithSubscribeOn() throws InterruptedException {

        final AtomicInteger counter = new AtomicInteger();
        Flowable<Integer> oi = Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(Subscriber<? super Integer> sub) {
                counter.incrementAndGet();
                sub.onNext(1);
                sub.onNext(2);
                sub.onComplete();
            }
        }).subscribeOn(Schedulers.newThread());

        Object[] ys = oi.repeat(Schedulers.newThread()).map(new Function<Integer, Integer>() {

            @Override
            public Integer call(Integer t1) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return t1;
            }

        }).take(4).toList().toBlocking().last().toArray();

        assertEquals(2, counter.get());
        assertArrayEquals(new Object[] { 1, 2, 1, 2 }, ys);
    }

    @Test(timeout = 2000)
    public void testRepeatAndTake() {
        @SuppressWarnings("unchecked")
                Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1).repeat().take(10).subscribe(o);
        
        verify(o, times(10)).onNext(1);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test(timeout = 2000)
    public void testRepeatLimited() {
        @SuppressWarnings("unchecked")
                Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1).repeat(10).subscribe(o);
        
        verify(o, times(10)).onNext(1);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test(timeout = 2000)
    public void testRepeatError() {
        @SuppressWarnings("unchecked")
                Observer<Object> o = mock(Observer.class);
        
        Flowable.error(new TestException()).repeat(10).subscribe(o);
        
        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
        
    }

    @Test(timeout = 2000)
    public void testRepeatZero() {
        @SuppressWarnings("unchecked")
                Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1).repeat(0).subscribe(o);
        
        verify(o).onComplete();
        verify(o, never()).onNext(any());
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test(timeout = 2000)
    public void testRepeatOne() {
        @SuppressWarnings("unchecked")
                Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1).repeat(1).subscribe(o);
        
        verify(o).onComplete();
        verify(o, times(1)).onNext(any());
        verify(o, never()).onError(any(Throwable.class));
    }
    
    /** Issue #2587. */
    @Test
    public void testRepeatAndDistinctUnbounded() {
        Flowable<Integer> src = Flowable.from(Arrays.asList(1, 2, 3, 4, 5))
                .take(3)
                .repeat(3)
                .distinct();
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        src.subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertTerminalEvent();
        ts.assertValues((1, 2, 3));
    }
}
