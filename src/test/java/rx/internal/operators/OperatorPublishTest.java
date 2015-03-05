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

import static org.junit.Assert.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Function;

import org.junit.Test;

import rx.*;
import rx.disposables.Disposable;
import rx.internal.subscriptions.AbstractSubscription;
import rx.observables.ConnectableObservable;
import rx.schedulers.Schedulers;
import rx.subscribers.TestSubscriber;

public class OperatorPublishTest {

    @Test
    public void testPublish() throws InterruptedException {
        final AtomicInteger counter = new AtomicInteger();
        Observable<String> f = Observable.create(observer ->
                new Thread(() -> {
                    counter.incrementAndGet();
                    AbstractSubscription.setEmptyOn(observer);
                    observer.onNext("one");
                    observer.onComplete();
                }).start()
        );
        ConnectableObservable<String> o = f.publish();

        final CountDownLatch latch = new CountDownLatch(2);

        // subscribe once
        o.subscribe(v -> {
            assertEquals("one", v);
            latch.countDown();
        });

        // subscribe again
        o.subscribe(v -> {
            assertEquals("one", v);
            latch.countDown();
        });

        Disposable s = o.connect();
        try {
            if (!latch.await(1000, TimeUnit.MILLISECONDS)) {
                fail("subscriptions did not receive values");
            }
            assertEquals(1, counter.get());
        } finally {
            s.dispose();
        }
    }

    @Test
    public void testBackpressureFastSlow() {
        ConnectableObservable<Integer> is = Observable.range(1, Flow.defaultBufferSize() * 2).publish();
        Observable<Integer> fast = is.observeOn(Schedulers.computation())
        .map(v -> v + 1000)
        .doOnComplete(() -> System.out.println("^^^^^^^^^^^^^ completed FAST"));
        
        
        Observable<Integer> slow = is.observeOn(Schedulers.computation())
        .map(new Function<Integer, Integer>() {
            int c = 0;

            @Override
            public Integer apply(Integer i) {
                if (c == 0) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
                c++;
                return i;
            }

        }).doOnComplete(() ->
                System.out.println("^^^^^^^^^^^^^ completed SLOW")
        );

        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Observable.merge(fast, slow).subscribe(ts);
        is.connect();
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(Flow.defaultBufferSize() * 4);
    }

    // use case from https://github.com/ReactiveX/RxJava/issues/1732
    @Test(timeout = 2000)
    public void testTakeUntilWithPublishedStreamUsingSelector() {
        final AtomicInteger emitted = new AtomicInteger();
        Observable<Integer> xs = Observable.range(0, Flow.defaultBufferSize() * 2)
                .doOnNext(t1 -> emitted.incrementAndGet());
        
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        xs.publish(new Function<Observable<Integer>, Observable<Integer>>() {

            @Override
            public Observable<Integer> apply(Observable<Integer> xs) {
                return xs.takeUntil(xs.skipWhile(i -> i <= 3));
            }

        }).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValues(0, 1, 2, 3);
        assertEquals(5, emitted.get());
        System.out.println(ts.getValues());
    }

    // use case from https://github.com/ReactiveX/RxJava/issues/1732
    @Test
    public void testTakeUntilWithPublishedStream() {
        Observable<Integer> xs = Observable.range(0, Flow.defaultBufferSize() * 2);
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        ConnectableObservable<Integer> xsp = xs.publish();
        xsp.takeUntil(xsp.skipWhile(i -> i <= 3)).subscribe(ts);
        xsp.connect();
        System.out.println(ts.getValues());
    }

    @Test(timeout = 10000)
    public void testBackpressureTwoConsumers() {
        final AtomicInteger sourceEmission = new AtomicInteger();
        final AtomicBoolean sourceUnsubscribed = new AtomicBoolean();
        final Observable<Integer> source = Observable.range(1, 100)
                .doOnNext(t1 -> sourceEmission.incrementAndGet())
                .doOnUnsubscribe(() -> sourceUnsubscribed.set(true)).share();
        ;
        
        final AtomicBoolean child1Unsubscribed = new AtomicBoolean();
        final AtomicBoolean child2Unsubscribed = new AtomicBoolean();

        final TestSubscriber<Integer> ts2 = new TestSubscriber<>();

        final TestSubscriber<Integer> ts1 = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                if (getValues().size() == 2) {
                    source.doOnUnsubscribe(() -> child2Unsubscribed.set(true))
                    .take(5).subscribe(ts2);
                }
                super.onNext(t);
            }
        };
        
        source.doOnUnsubscribe(() -> child1Unsubscribed.set(true))
        .take(5).subscribe(ts1);
        
        ts1.awaitTerminalEvent();
        ts2.awaitTerminalEvent();
        
        ts1.assertNoErrors();
        ts2.assertNoErrors();
        
        assertTrue(sourceUnsubscribed.get());
        assertTrue(child1Unsubscribed.get());
        assertTrue(child2Unsubscribed.get());
        
        ts1.assertValues(1, 2, 3, 4, 5);
        ts2.assertValues(4, 5, 6, 7, 8);
        
        assertEquals(8, sourceEmission.get());
    }
}
