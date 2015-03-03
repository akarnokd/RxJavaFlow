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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import rx.Flowable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Function;
import rx.functions.BiFunction;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.AsyncSubject;
import rx.subjects.BehaviorSubject;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;
import rx.subjects.Subject;

public class OnSubscribeCacheTest {

    @Test
    public void testCache() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        Flowable<String> o = Flowable.create(new Flowable.OnSubscribe<String>() {

            @Override
            public void call(final Subscriber<? super String> observer) {
                new Thread(new Runnable() {

                    @Override
                    public void run() {
                        counter.incrementAndGet();
                        System.out.println("published observable being executed");
                        observer.onNext("one");
                        observer.onComplete();
                    }
                }).start();
            }
        }).cache();

        // we then expect the following 2 subscriptions to get that same value
        final CountDownLatch latch = new CountDownLatch(2);

        // subscribe once
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String v) {
                assertEquals("one", v);
                System.out.println("v: " + v);
                latch.countDown();
            }
        });

        // subscribe again
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String v) {
                assertEquals("one", v);
                System.out.println("v: " + v);
                latch.countDown();
            }
        });

        if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
            fail("subscriptions did not receive values");
        }
        assertEquals(1, counter.get());
    }

    private void testWithCustomSubjectAndRepeat(Subject<Integer, Integer> subject, Integer... expected) {
        Flowable<Integer> source0 = Flowable.just(1, 2, 3)
                .subscribeOn(Schedulers.io())
                .flatMap(new Function<Integer, Flowable<Integer>>() {
                    @Override
                    public Flowable<Integer> call(final Integer i) {
                        return Flowable.timer(i * 20, TimeUnit.MILLISECONDS).map(new Function<Long, Integer>() {
                            @Override
                            public Integer call(Long t1) {
                                return i;
                            }
                        });
                    }
                });

        Flowable<Integer> source1 = Flowable.create(new OnSubscribeCache<Integer>(source0, subject));

        Flowable<Integer> source2 = source1
                .repeat(4)
                .zipWith(Flowable.timer(0, 10, TimeUnit.MILLISECONDS, Schedulers.newThread()), new BiFunction<Integer, Long, Integer>() {
                    @Override
                    public Integer call(Integer t1, Long t2) {
                        return t1;
                    }

                });
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        source2.subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        System.out.println(ts.getOnNextEvents());
        ts.assertValues((expected));
    }

    @Test(timeout = 10000)
    public void testWithAsyncSubjectAndRepeat() {
        testWithCustomSubjectAndRepeat(AsyncSubject.<Integer> create(), 3, 3, 3, 3);
    }

    @Test(timeout = 10000)
    public void testWithBehaviorSubjectAndRepeat() {
        // BehaviorSubject just completes when repeated
        testWithCustomSubjectAndRepeat(BehaviorSubject.create(0), 0, 1, 2, 3);
    }

    @Test(timeout = 10000)
    public void testWithPublishSubjectAndRepeat() {
        // PublishSubject just completes when repeated
        testWithCustomSubjectAndRepeat(PublishSubject.<Integer> create(), 1, 2, 3);
    }

    @Test
    public void testWithReplaySubjectAndRepeat() {
        testWithCustomSubjectAndRepeat(ReplaySubject.<Integer> create(), 1, 2, 3, 1, 2, 3, 1, 2, 3, 1, 2, 3);
    }

    @Test
    public void testUnsubscribeSource() {
        Action0 unsubscribe = mock(Action0.class);
        Flowable<Integer> o = Flowable.just(1).doOnUnsubscribe(unsubscribe).cache();
        o.subscribe();
        o.subscribe();
        o.subscribe();
        verify(unsubscribe, times(1)).call();
    }
}
