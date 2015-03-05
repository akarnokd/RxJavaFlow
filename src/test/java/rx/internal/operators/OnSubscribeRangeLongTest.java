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

package rx.internal.operators;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import rx.*;
import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.subscribers.*;

/**
 * 
 */
public class OnSubscribeRangeLongTest {
    @Test
    public void simple() {
        TestSubscriber<Long> ts = new TestSubscriber<>(0);
        
        Observable<Long> source = Observable.rangeLong(1, 5);
        
        source.subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoTerminalEvent();
        
        ts.request(2);
        
        ts.assertValues(1L, 2L);
        ts.assertNoTerminalEvent();
        
        ts.request(6);
        
        ts.assertValues(1L, 2L, 3L, 4L, 5L);
        
        ts.assertNoErrors();
        ts.assertComplete();
    }
    @Test
    public void empty() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Observable<Integer> source = Observable.range(1, 0);
        
        source.subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();
    }
    @Test
    public void unbounded() {
        TestSubscriber<Long> ts = new TestSubscriber<Long>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        Observable<Long> source = Observable.rangeLong(1, 5);
        
        source.subscribe(ts);
        
        ts.assertValues(1L, 2L, 3L, 4L, 5L);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    @Test
    public void takeSome() {
        TestSubscriber<Long> ts = new TestSubscriber<Long>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        Observable<Long> source = Observable.rangeLong(1, 5).take(2);
        
        source.subscribe(ts);
        
        ts.assertValues(1L, 2L);
        ts.assertNoErrors();
        ts.assertComplete();
    }

    @Test
    public void testRangeStartAt2Count3() {
        @SuppressWarnings("unchecked")
        Subscriber<Long> observer = mock(Subscriber.class);
        TestSubscriber<Long> ts = new TestSubscriber<>(observer);
        Observable.rangeLong(2, 3).subscribe(ts);

        verify(observer, times(1)).onNext(2L);
        verify(observer, times(1)).onNext(3L);
        verify(observer, times(1)).onNext(4L);
        verify(observer, never()).onNext(5L);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testRangeUnsubscribe() {
        @SuppressWarnings("unchecked")
        Subscriber<Long> observer = mock(Subscriber.class);
        TestSubscriber<Long> ts = new TestSubscriber<>(observer);
        
        final AtomicInteger count = new AtomicInteger();
        Observable.rangeLong(1, 1000).doOnNext(t1 -> count.incrementAndGet())
        .take(3).subscribe(ts);

        verify(observer, times(1)).onNext(1L);
        verify(observer, times(1)).onNext(2L);
        verify(observer, times(1)).onNext(3L);
        verify(observer, never()).onNext(4L);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
        assertEquals(3, count.get());
    }

    @Test
    public void testRangeWithOverflow() {
        Observable.rangeLong(1, 0);
    }

    @Test
    public void testRangeWithOverflow2() {
        Observable.rangeLong(Long.MAX_VALUE, 0);
    }

    @Test
    public void testRangeWithOverflow3() {
        Observable.rangeLong(1, Long.MAX_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangeWithOverflow4() {
        Observable.rangeLong(2, Long.MAX_VALUE);
    }

    @Test
    public void testRangeWithOverflow5() {
        assertFalse(Observable.rangeLong(Long.MIN_VALUE, 0).toBlocking().getIterator().hasNext());
    }

    @Test
    public void testBackpressureViaRequest() {
        Observable<Long> o = Observable.rangeLong(1, Flow.defaultBufferSize());
        TestSubscriber<Long> ts = new TestSubscriber<>(0);
        
        o.subscribe(ts);
        
        ts.assertNoValues();
        ts.requestMore(1);
        ts.assertValues(1L);
        ts.requestMore(2);
        ts.assertValues(1L, 2L, 3L);
        ts.requestMore(3);
        ts.assertValues(1L, 2L, 3L, 4L, 5L, 6L);
        ts.requestMore(Flow.defaultBufferSize());
        ts.assertTerminalEvent();
    }

    @Test
    public void testNoBackpressure() {
        ArrayList<Long> list = new ArrayList<>(Flow.defaultBufferSize() * 2);
        for (long i = 1; i <= Flow.defaultBufferSize() * 2 + 1; i++) {
            list.add(i);
        }

        Observable<Long> o = Observable.rangeLong(1, list.size());
        TestSubscriber<Long> ts = new TestSubscriber<>(0);
        o.subscribe(ts);
        ts.assertNoValues();
        ts.requestMore(Long.MAX_VALUE); // infinite
        ts.assertValues(list);
        ts.assertTerminalEvent();
    }
    void testWithBackpressureOneByOne(int start) {
        Observable<Long> source = Observable.rangeLong(start, 100);
        
        TestSubscriber<Long> ts = new TestSubscriber<>(0);
        source.subscribe(ts);
        ts.requestMore(1);
        
        List<Long> list = new ArrayList<>(100);
        for (long i = 0; i < 100; i++) {
            list.add(i + start);
            ts.requestMore(1);
        }
        ts.assertValues(list);
        ts.assertTerminalEvent();
    }
    void testWithBackpressureAllAtOnce(int start) {
        Observable<Long> source = Observable.rangeLong(start, 100);
        
        TestSubscriber<Long> ts = new TestSubscriber<>(0);
        source.subscribe(ts);
        ts.requestMore(100);
        
        List<Long> list = new ArrayList<>(100);
        for (long i = 0; i < 100; i++) {
            list.add(i + start);
        }
        ts.assertValues(list);
        ts.assertTerminalEvent();
    }
    @Test
    public void testWithBackpressure1() {
        for (int i = 0; i < 100; i++) {
            testWithBackpressureOneByOne(i);
        }
    }
    @Test
    public void testWithBackpressureAllAtOnce() {
        for (int i = 0; i < 100; i++) {
            testWithBackpressureAllAtOnce(i);
        }
    }
    @Test
    public void testWithBackpressureRequestWayMore() {
        Observable<Long> source = Observable.rangeLong(50, 100);
        
        TestSubscriber<Long> ts = new TestSubscriber<>(0);
        source.subscribe(ts);
        ts.requestMore(150);
        
        List<Long> list = new ArrayList<>(100);
        for (long i = 0; i < 100; i++) {
            list.add(i + 50);
        }
        
        ts.requestMore(50); // and then some
        
        ts.assertValues(list);
        ts.assertTerminalEvent();
    }
    
    @Test
    public void testRequestOverflow() {
        final AtomicInteger count = new AtomicInteger();
        int n = 10;
        Observable.rangeLong(1, n).subscribe(new AbstractSubscriber<Long>() {

            @Override
            public void onSubscribe() {
                subscription.request(2);
            }
            
            @Override
            public void onComplete() {
                //do nothing
            }

            @Override
            public void onError(Throwable e) {
                throw new RuntimeException(e);
            }

            @Override
            public void onNext(Long t) {
                count.incrementAndGet();
                subscription.request(Long.MAX_VALUE - 1);
            }});
        assertEquals(n, count.get());
    }
}
