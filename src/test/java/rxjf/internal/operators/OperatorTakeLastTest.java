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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.Test;
import org.mockito.InOrder;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.schedulers.Schedulers;
import rxjf.subscribers.*;

public class OperatorTakeLastTest {

    @Test
    public void testTakeLastEmpty() {
        Flowable<String> w = Flowable.empty();
        Flowable<String> take = w.takeLast(2);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        take.subscribe(observer);
        verify(observer, never()).onNext(any(String.class));
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testTakeLast1() {
        Flowable<String> w = Flowable.from("one", "two", "three");
        Flowable<String> take = w.takeLast(2);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        InOrder inOrder = inOrder(observer);
        take.subscribe(observer);
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        verify(observer, never()).onNext("one");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testTakeLast2() {
        Flowable<String> w = Flowable.just("one");
        Flowable<String> take = w.takeLast(10);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        take.subscribe(observer);
        verify(observer, times(1)).onNext("one");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testTakeLastWithZeroCount() {
        Flowable<String> w = Flowable.just("one");
        Flowable<String> take = w.takeLast(0);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        take.subscribe(observer);
        verify(observer, never()).onNext("one");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testTakeLastWithNull() {
        Flowable<String> w = Flowable.from("one", null, "three");
        Flowable<String> take = w.takeLast(2);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        take.subscribe(observer);
        verify(observer, never()).onNext("one");
        verify(observer, times(1)).onNext(null);
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testTakeLastWithNegativeCount() {
        Flowable.just("one").takeLast(-1);
    }

    @Test
    public void testBackpressure1() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Flowable.range(1, 100000).takeLast(1).observeOn(Schedulers.newThread()).map(newSlowProcessor()).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValues(100000);
    }

    @Test
    public void testBackpressure2() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Flowable.range(1, 100000).takeLast(Flow.defaultBufferSize() * 4).observeOn(Schedulers.newThread()).map(newSlowProcessor()).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(Flow.defaultBufferSize() * 4, ts.getOnNextEvents().size());
    }

    private Function<Integer, Integer> newSlowProcessor() {
        return new Function<Integer, Integer>() {
            int c = 0;

            @Override
            public Integer apply(Integer i) {
                if (c++ < 100) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                    }
                }
                return i;
            }

        };
    }

    @Test
    public void testIssue1522() {
        // https://github.com/ReactiveX/RxJava/issues/1522
        assertEquals(0, Flowable
                .empty()
                .count()
                .filter(v -> false)
                .toList()
                .toBlocking().single().size());
    }

    @Test
    public void testIgnoreRequest1() {
        // If `takeLast` does not ignore `request` properly, StackOverflowError will be thrown.
        Flowable.range(0, 100000).takeLast(100000)
        .subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Integer integer) {
                subscription.request(Long.MAX_VALUE);
            }
        });
    }

    @Test
    public void testIgnoreRequest2() {
        // If `takeLast` does not ignore `request` properly, StackOverflowError will be thrown.
        Flowable.range(0, 100000).takeLast(100000)
        .subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onSubscribe() {
                subscription.request(1);
            }

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Integer integer) {
                subscription.request(1);
            }
        });
    }

    @Test(timeout = 30000)
    public void testIgnoreRequest3() {
        // If `takeLast` does not ignore `request` properly, it will enter an infinite loop.
        Flowable.range(0, 100000).takeLast(100000)
        .subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onSubscribe() {
                subscription.request(1);
            }

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Integer integer) {
                subscription.request(Long.MAX_VALUE);
            }
        });
    }


    @Test
    public void testIgnoreRequest4() {
        // If `takeLast` does not ignore `request` properly, StackOverflowError will be thrown.
        Flowable.range(0, 100000).takeLast(100000)
        .subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Integer integer) {
                subscription.request(1);
            }
        });
    }
    
    @Test
    public void testUnsubscribeTakesEffectEarlyOnFastPath() {
        final AtomicInteger count = new AtomicInteger();
        Flowable.range(0, 100000).takeLast(100000)
        .subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Integer integer) {
                count.incrementAndGet();
                subscription.cancel();
            }
        });
        assertEquals(1,count.get());
    }
}
