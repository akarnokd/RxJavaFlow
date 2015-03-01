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

package rxjf.internal.operators;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.subscribers.*;

/**
 * 
 */
public class OnSubscribeRangeTest {
    @Test
    public void simple() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        Flowable<Integer> source = Flowable.range(1, 5);
        
        source.subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoTerminalEvent();
        
        ts.request(2);
        
        ts.assertValues(1, 2);
        ts.assertNoTerminalEvent();
        
        ts.request(6);
        
        ts.assertValues(1, 2, 3, 4, 5);
        
        ts.assertNoErrors();
        ts.assertComplete();
    }
    @Test
    public void empty() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Flowable<Integer> source = Flowable.range(1, 0);
        
        source.subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();
    }
    @Test
    public void unbounded() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        Flowable<Integer> source = Flowable.range(1, 5);
        
        source.subscribe(ts);
        
        ts.assertValues(1, 2, 3, 4, 5);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    @Test
    public void takeSome() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        Flowable<Integer> source = Flowable.range(1, 5).take(2);
        
        source.subscribe(ts);
        
        ts.assertValues(1, 2);
        ts.assertNoErrors();
        ts.assertComplete();
    }

    @Test
    public void testRangeStartAt2Count3() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);
        Flowable.range(2, 3).subscribe(ts);

        verify(observer, times(1)).onNext(2);
        verify(observer, times(1)).onNext(3);
        verify(observer, times(1)).onNext(4);
        verify(observer, never()).onNext(5);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testRangeUnsubscribe() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);
        
        final AtomicInteger count = new AtomicInteger();
        Flowable.range(1, 1000).doOnNext(t1 -> count.incrementAndGet())
        .take(3).subscribe(ts);

        verify(observer, times(1)).onNext(1);
        verify(observer, times(1)).onNext(2);
        verify(observer, times(1)).onNext(3);
        verify(observer, never()).onNext(4);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
        assertEquals(3, count.get());
    }

    @Test
    public void testRangeWithOverflow() {
        Flowable.range(1, 0);
    }

    @Test
    public void testRangeWithOverflow2() {
        Flowable.range(Integer.MAX_VALUE, 0);
    }

    @Test
    public void testRangeWithOverflow3() {
        Flowable.range(1, Integer.MAX_VALUE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRangeWithOverflow4() {
        Flowable.range(2, Integer.MAX_VALUE);
    }

    @Test
    public void testRangeWithOverflow5() {
        assertFalse(Flowable.range(Integer.MIN_VALUE, 0).toBlocking().getIterator().hasNext());
    }

    @Test
    public void testBackpressureViaRequest() {
        Flowable<Integer> o = Flowable.range(1, Flow.defaultBufferSize());
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        o.subscribe(ts);
        
        ts.assertNoValues();
        ts.requestMore(1);
        ts.assertValues(1);
        ts.requestMore(2);
        ts.assertValues(1, 2, 3);
        ts.requestMore(3);
        ts.assertValues(1, 2, 3, 4, 5, 6);
        ts.requestMore(Flow.defaultBufferSize());
        ts.assertTerminalEvent();
    }

    @Test
    public void testNoBackpressure() {
        ArrayList<Integer> list = new ArrayList<>(Flow.defaultBufferSize() * 2);
        for (int i = 1; i <= Flow.defaultBufferSize() * 2 + 1; i++) {
            list.add(i);
        }

        Flowable<Integer> o = Flowable.range(1, list.size());
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        o.subscribe(ts);
        ts.assertNoValues();
        ts.requestMore(Long.MAX_VALUE); // infinite
        ts.assertValues(list);
        ts.assertTerminalEvent();
    }
    void testWithBackpressureOneByOne(int start) {
        Flowable<Integer> source = Flowable.range(start, 100);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        source.subscribe(ts);
        ts.assertNoValues();
        ts.requestMore(1);
        
        List<Integer> list = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            list.add(i + start);
            ts.requestMore(1);
        }
        ts.assertValues(list);
        ts.assertTerminalEvent();
    }
    void testWithBackpressureAllAtOnce(int start) {
        Flowable<Integer> source = Flowable.range(start, 100);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        source.subscribe(ts);
        ts.assertNoValues();
        ts.requestMore(100);
        
        List<Integer> list = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
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
        Flowable<Integer> source = Flowable.range(50, 100);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        source.subscribe(ts);
        ts.requestMore(150);
        
        List<Integer> list = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
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
        Flowable.range(1, n).subscribe(new AbstractSubscriber<Integer>() {

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
            public void onNext(Integer t) {
                count.incrementAndGet();
                subscription.request(Long.MAX_VALUE - 1);
            }});
        assertEquals(n, count.get());
    }

}
