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

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;

import org.junit.Test;
import org.mockito.Mockito;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.schedulers.Schedulers;
import rxjf.subscribers.*;

/**
 * 
 */
public class OnSubscribeIterableTest {
    @Test
    public void simple() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        Flowable<Integer> source = Flowable.from(Arrays.asList(1, 2, 3, 4, 5));
        
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
        
        Flowable<Integer> source = Flowable.from(Arrays.asList());
        
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
        Flowable<Integer> source = Flowable.from(Arrays.asList(1, 2, 3, 4, 5));
        
        source.subscribe(ts);
        
        ts.assertValues(1, 2, 3, 4, 5);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    @Test(expected = NullPointerException.class)
    public void testNull() {
        Flowable.from((Iterable<String>)null);
    }
    
    @Test
    public void testListIterable() {
        Flowable<String> observable = Flowable.from(Arrays.asList("one", "two", "three"));

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        
        observable.subscribe(ts);
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    /**
     * This tests the path that can not optimize based on size so must use setProducer.
     */
    @Test
    public void testRawIterable() {
        Iterable<String> it = new Iterable<String>() {

            @Override
            public Iterator<String> iterator() {
                return new Iterator<String>() {

                    int i = 0;

                    @Override
                    public boolean hasNext() {
                        return i < 3;
                    }

                    @Override
                    public String next() {
                        return String.valueOf(++i);
                    }

                    @Override
                    public void remove() {
                    }

                };
            }

        };
        Flowable<String> observable = Flowable.from(it);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        observable.subscribe(ts);
        
        verify(observer, times(1)).onNext("1");
        verify(observer, times(1)).onNext("2");
        verify(observer, times(1)).onNext("3");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testFlowableFromIterable() {
        Flowable<String> observable = Flowable.from(Arrays.<String> asList("one", "two", "three"));

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        observable.subscribe(ts);
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testBackpressureViaRequest() {
        ArrayList<Integer> list = new ArrayList<>(Flow.defaultBufferSize());
        for (int i = 1; i <= Flow.defaultBufferSize() + 1; i++) {
            list.add(i);
        }
        Flowable<Integer> o = Flowable.from(list);
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        o.subscribe(ts);
        ts.assertNoValues();
        ts.requestMore(1);
        
        ts.assertValues(1);
        ts.requestMore(2);
        ts.assertValues(1, 2, 3);
        ts.requestMore(3);
        ts.assertValues(1, 2, 3, 4, 5, 6);
        ts.requestMore(list.size());
        ts.assertTerminalEvent();
        ts.assertNoErrors();
    }

    @Test
    public void testNoBackpressure() {
        Flowable<Integer> o = Flowable.from(Arrays.asList(1, 2, 3, 4, 5));
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        o.subscribe(ts);
        ts.assertNoValues();
        ts.requestMore(Long.MAX_VALUE); // infinite
        ts.assertValues(1, 2, 3, 4, 5);
        ts.assertTerminalEvent();
        ts.assertNoErrors();
    }

    @Test
    public void testSubscribeMultipleTimes() {
        Flowable<Integer> o = Flowable.from(Arrays.asList(1, 2, 3));
        for (int i = 0; i < 4; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            o.subscribe(ts);
            
            ts.assertValues(1, 2, 3);
            ts.assertComplete();
            ts.assertNoErrors();
        }
    }
    
    @Test
    public void testFromIterableRequestOverflow() throws InterruptedException {
        Flowable<Integer> o = Flowable.from(Arrays.asList(1, 2, 3, 4));
        final int expectedCount = 4;
        final CountDownLatch latch = new CountDownLatch(expectedCount);
        o.subscribeOn(Schedulers.computation()).subscribe(new AbstractSubscriber<Integer>() {
            
            @Override
            public void onSubscribe() {
                subscription.request(2);
            }

            @Override
            public void onComplete() {
                //ignore
            }

            @Override
            public void onError(Throwable e) {
                throw new RuntimeException(e);
            }

            @Override
            public void onNext(Integer t) {
                latch.countDown();
                subscription.request(Long.MAX_VALUE-1);
            }});
        
        assertTrue(latch.await(2, TimeUnit.SECONDS));
    }

}
