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

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.junit.*;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.Flowable.OnSubscribe;
import rxjf.disposables.Disposable;
import rxjf.internal.subscriptions.*;
import rxjf.schedulers.*;
import rxjf.subscribers.*;

public class OperatorMergeTest {

    @Test
    public void testMergeFlowableOfFlowables() {
        final Flowable<String> o1 = Flowable.create(new TestSynchronousFlowable());
        final Flowable<String> o2 = Flowable.create(new TestSynchronousFlowable());

        Flowable<Flowable<String>> observableOfFlowables = Flowable.create(new Flowable.OnSubscribe<Flowable<String>>() {

            @Override
            public void accept(Subscriber<? super Flowable<String>> observer) {
                // simulate what would happen in an observable
                AbstractSubscription.setEmptyOn(observer);
                observer.onNext(o1);
                observer.onNext(o2);
                observer.onComplete();
            }

        });
        Flowable<String> m = Flowable.merge(observableOfFlowables);
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(1)).onComplete();
        verify(stringObserver, times(2)).onNext("hello");
    }

    @Test
    public void testMergeArray() {
        final Flowable<String> o1 = Flowable.create(new TestSynchronousFlowable());
        final Flowable<String> o2 = Flowable.create(new TestSynchronousFlowable());

        Flowable<String> m = Flowable.merge(o1, o2);
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(2)).onNext("hello");
        verify(stringObserver, times(1)).onComplete();
    }

    @Test
    public void testMergeList() {
        final Flowable<String> o1 = Flowable.create(new TestSynchronousFlowable());
        final Flowable<String> o2 = Flowable.create(new TestSynchronousFlowable());
        List<Flowable<String>> listOfFlowables = new ArrayList<>();
        listOfFlowables.add(o1);
        listOfFlowables.add(o2);

        Flowable<String> m = Flowable.merge(listOfFlowables);
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(1)).onComplete();
        verify(stringObserver, times(2)).onNext("hello");
    }

    @Test(timeout = 1000)
    public void testUnSubscribeFlowableOfFlowables() throws InterruptedException {

        final AtomicBoolean unsubscribed = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);

        Flowable<Flowable<Long>> source = Flowable.create(new Flowable.OnSubscribe<Flowable<Long>>() {

            @Override
            public void accept(final Subscriber<? super Flowable<Long>> observer) {
                // verbose on purpose so I can track the inside of it
                final Disposable s = Disposable.from(() -> {
                    System.out.println("*** unsubscribed");
                    unsubscribed.set(true);
                });
                
                DisposableSubscription ds = DisposableSubscription.createEmpty(observer);
                ds.add(s);
                
                observer.onSubscribe(ds);

                new Thread(new Runnable() {

                    @Override
                    public void run() {

                        while (!unsubscribed.get()) {
                            observer.onNext(Flowable.just(1L, 2L));
                        }
                        System.out.println("Done looping after unsubscribe: " + unsubscribed.get());
                        observer.onComplete();

                        // mark that the thread is finished
                        latch.countDown();
                    }
                }).start();
            }

        });

        final AtomicInteger count = new AtomicInteger();
        Flowable.merge(source).take(6).toBlocking().forEach(v -> {
                System.out.println("Value: " + v);
                int c = count.incrementAndGet();
                if (c > 6) {
                    fail("Should be only 6");
                }
        });

        latch.await(1000, TimeUnit.MILLISECONDS);

        System.out.println("unsubscribed: " + unsubscribed.get());

        assertTrue(unsubscribed.get());

    }

    @Test
    public void testMergeArrayWithThreading() {
        final TestASynchronousFlowable o1 = new TestASynchronousFlowable();
        final TestASynchronousFlowable o2 = new TestASynchronousFlowable();

        Flowable<String> m = Flowable.merge(Flowable.create(o1), Flowable.create(o2));
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(2)).onNext("hello");
        verify(stringObserver, times(1)).onComplete();
    }

    @Test
    public void testSynchronizationOfMultipleSequences() throws Throwable {
        final TestASynchronousFlowable o1 = new TestASynchronousFlowable();
        final TestASynchronousFlowable o2 = new TestASynchronousFlowable();

        // use this latch to cause onNext to wait until we're ready to let it go
        final CountDownLatch endLatch = new CountDownLatch(1);

        final AtomicInteger concurrentCounter = new AtomicInteger();
        final AtomicInteger totalCounter = new AtomicInteger();

        Flowable<String> m = Flowable.merge(Flowable.create(o1), Flowable.create(o2));
        m.subscribe(new AbstractSubscriber<String>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                throw new RuntimeException("failed", e);
            }

            @Override
            public void onNext(String v) {
                totalCounter.incrementAndGet();
                concurrentCounter.incrementAndGet();
                try {
                    // wait here until we're done asserting
                    endLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    throw new RuntimeException("failed", e);
                } finally {
                    concurrentCounter.decrementAndGet();
                }
            }

        });

        // wait for both observables to send (one should be blocked)
        o1.onNextBeingSent.await();
        o2.onNextBeingSent.await();

        // I can't think of a way to know for sure that both threads have or are trying to send onNext
        // since I can't use a CountDownLatch for "after" onNext since I want to catch during it
        // but I can't know for sure onNext is invoked
        // so I'm unfortunately reverting to using a Thread.sleep to allow the process scheduler time
        // to make sure after o1.onNextBeingSent and o2.onNextBeingSent are hit that the following
        // onNext is invoked.

        Thread.sleep(300);

        try { // in try/finally so threads are released via latch countDown even if assertion fails
            assertEquals(1, concurrentCounter.get());
        } finally {
            // release so it can finish
            endLatch.countDown();
        }

        try {
            o1.t.join();
            o2.t.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        assertEquals(2, totalCounter.get());
        assertEquals(0, concurrentCounter.get());
    }

    /**
     * unit test from OperationMergeDelayError backported here to show how these use cases work with normal merge
     */
    @Test
    public void testError1() {
        // we are using synchronous execution to test this exactly rather than non-deterministic concurrent behavior
        final Flowable<String> o1 = Flowable.create(new TestErrorFlowable("four", null, "six")); // we expect to lose "six"
        final Flowable<String> o2 = Flowable.create(new TestErrorFlowable("one", "two", "three")); // we expect to lose all of these since o1 is done first and fails

        Flowable<String> m = Flowable.merge(o1, o2);
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        verify(stringObserver, times(1)).onError(any(NullPointerException.class));
        verify(stringObserver, never()).onComplete();
        verify(stringObserver, times(0)).onNext("one");
        verify(stringObserver, times(0)).onNext("two");
        verify(stringObserver, times(0)).onNext("three");
        verify(stringObserver, times(1)).onNext("four");
        verify(stringObserver, times(0)).onNext("five");
        verify(stringObserver, times(0)).onNext("six");
    }

    /**
     * unit test from OperationMergeDelayError backported here to show how these use cases work with normal merge
     */
    @Test
    public void testError2() {
        // we are using synchronous execution to test this exactly rather than non-deterministic concurrent behavior
        final Flowable<String> o1 = Flowable.create(new TestErrorFlowable("one", "two", "three"));
        final Flowable<String> o2 = Flowable.create(new TestErrorFlowable("four", null, "six")); // we expect to lose "six"
        final Flowable<String> o3 = Flowable.create(new TestErrorFlowable("seven", "eight", null));// we expect to lose all of these since o2 is done first and fails
        final Flowable<String> o4 = Flowable.create(new TestErrorFlowable("nine"));// we expect to lose all of these since o2 is done first and fails

        Flowable<String> m = Flowable.merge(o1, o2, o3, o4);
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        verify(stringObserver, times(1)).onError(any(NullPointerException.class));
        verify(stringObserver, never()).onComplete();
        verify(stringObserver, times(1)).onNext("one");
        verify(stringObserver, times(1)).onNext("two");
        verify(stringObserver, times(1)).onNext("three");
        verify(stringObserver, times(1)).onNext("four");
        verify(stringObserver, times(0)).onNext("five");
        verify(stringObserver, times(0)).onNext("six");
        verify(stringObserver, times(0)).onNext("seven");
        verify(stringObserver, times(0)).onNext("eight");
        verify(stringObserver, times(0)).onNext("nine");
    }

    @Test
    public void testThrownErrorHandling() {
        TestSubscriber<String> ts = new TestSubscriber<>();
        Flowable<String> o1 = Flowable.create(new OnSubscribe<String>() {

            @Override
            public void accept(Subscriber<? super String> s) {
                AbstractSubscription.setEmptyOn(s);
                throw new RuntimeException("fail");
            }

        });

        Flowable.merge(o1, o1).subscribe(ts);
        ts.awaitTerminalEvent(1000, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
        System.out.println("Error: " + ts.getErrors());
    }

    private static class TestSynchronousFlowable implements Flowable.OnSubscribe<String> {

        @Override
        public void accept(Subscriber<? super String> observer) {
            AbstractSubscription.setEmptyOn(observer);
            observer.onNext("hello");
            observer.onComplete();
        }
    }

    private static class TestASynchronousFlowable implements Flowable.OnSubscribe<String> {
        Thread t;
        final CountDownLatch onNextBeingSent = new CountDownLatch(1);

        @Override
        public void accept(final Subscriber<? super String> observer) {
            t = new Thread(new Runnable() {

                @Override
                public void run() {
                    AbstractSubscription.setEmptyOn(observer);
                    onNextBeingSent.countDown();
                    try {
                        observer.onNext("hello");
                        // I can't use a countDownLatch to prove we are actually sending 'onNext'
                        // since it will block if synchronized and I'll deadlock
                        observer.onComplete();
                    } catch (Exception e) {
                        observer.onError(e);
                    }
                }

            });
            t.start();
        }
    }

    private static class TestErrorFlowable implements Flowable.OnSubscribe<String> {

        String[] valuesToReturn;

        TestErrorFlowable(String... values) {
            valuesToReturn = values;
        }

        @Override
        public void accept(Subscriber<? super String> observer) {
            AbstractSubscription.setEmptyOn(observer);
            for (String s : valuesToReturn) {
                if (s == null) {
                    System.out.println("throwing exception");
                    observer.onError(new NullPointerException());
                } else {
                    observer.onNext(s);
                }
            }
            observer.onComplete();
        }
    }

    @Test
    public void testUnsubscribeAsFlowablesComplete() {
        TestScheduler scheduler1 = Schedulers.test();
        AtomicBoolean os1 = new AtomicBoolean(false);
        Flowable<Long> o1 = createFlowableOf5IntervalsOf1SecondIncrementsWithSubscriptionHook(scheduler1, os1);

        TestScheduler scheduler2 = Schedulers.test();
        AtomicBoolean os2 = new AtomicBoolean(false);
        Flowable<Long> o2 = createFlowableOf5IntervalsOf1SecondIncrementsWithSubscriptionHook(scheduler2, os2);

        TestSubscriber<Long> ts = new TestSubscriber<>();
        Flowable.merge(o1, o2).subscribe(ts);

        // we haven't incremented time so nothing should be received yet
        ts.assertNoValues();

        scheduler1.advanceTimeBy(3, TimeUnit.SECONDS);
        scheduler2.advanceTimeBy(2, TimeUnit.SECONDS);

        ts.assertValues(0L, 1L, 2L, 0L, 1L);
        // not unsubscribed yet
        assertFalse(os1.get());
        assertFalse(os2.get());

        // advance to the end at which point it should complete
        scheduler1.advanceTimeBy(3, TimeUnit.SECONDS);

        ts.assertValues(0L, 1L, 2L, 0L, 1L, 3L, 4L);
        assertTrue(os1.get());
        assertFalse(os2.get());

        // both should be completed now
        scheduler2.advanceTimeBy(3, TimeUnit.SECONDS);

        ts.assertValues(0L, 1L, 2L, 0L, 1L, 3L, 4L, 2L, 3L, 4L);
        assertTrue(os1.get());
        assertTrue(os2.get());

        ts.assertTerminalEvent();
    }

    @Test
    public void testEarlyUnsubscribe() {
        for (int i = 0; i < 10; i++) {
            TestScheduler scheduler1 = Schedulers.test();
            AtomicBoolean os1 = new AtomicBoolean(false);
            Flowable<Long> o1 = createFlowableOf5IntervalsOf1SecondIncrementsWithSubscriptionHook(scheduler1, os1);

            TestScheduler scheduler2 = Schedulers.test();
            AtomicBoolean os2 = new AtomicBoolean(false);
            Flowable<Long> o2 = createFlowableOf5IntervalsOf1SecondIncrementsWithSubscriptionHook(scheduler2, os2);

            TestSubscriber<Long> ts = new TestSubscriber<>();
            Disposable s = Flowable.merge(o1, o2).subscribeDisposable(ts);

            // we haven't incremented time so nothing should be received yet
            ts.assertNoValues();

            scheduler1.advanceTimeBy(3, TimeUnit.SECONDS);
            scheduler2.advanceTimeBy(2, TimeUnit.SECONDS);

            ts.assertValues(0L, 1L, 2L, 0L, 1L);
            // not unsubscribed yet
            assertFalse(os1.get());
            assertFalse(os2.get());

            // early unsubscribe
            s.dispose();

            assertTrue(os1.get());
            assertTrue(os2.get());

            ts.assertValues(0L, 1L, 2L, 0L, 1L);
            assertTrue(s.isDisposed());
        }
    }

    private Flowable<Long> createFlowableOf5IntervalsOf1SecondIncrementsWithSubscriptionHook(final Scheduler scheduler, final AtomicBoolean unsubscribed) {
        return Flowable.create(s -> {
            AbstractDisposableSubscriber<? super Long> d = DefaultDisposableSubscriber.wrap(s);
            d.add(Disposable.from(() -> unsubscribed.set(true)));
            
            Flowable.interval(1, TimeUnit.SECONDS, scheduler).take(5).subscribe(s);
        });
    }

    @Test(timeout = 10000)
    public void testConcurrency() {
        Flowable<Integer> o = Flowable.range(1, 10000).subscribeOn(Schedulers.newThread());

        for (int i = 0; i < 10; i++) {
            Flowable<Integer> merge = Flowable.merge(o.onBackpressureBuffer(), o.onBackpressureBuffer(), o.onBackpressureBuffer());
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            merge.subscribe(ts);

            ts.awaitTerminalEvent();
            ts.assertNoErrors();
            ts.assertComplete();
            ts.assertValueCount(30000);
            //            System.out.println("onNext: " + onNextEvents.size() + " onComplete(): " + ts.getonComplete()Events().size());
        }
    }

    @Test
    public void testConcurrencyWithSleeping() {

        Flowable<Integer> o = Flowable.create(new OnSubscribe<Integer>() {
            @Override
            public void accept(final Subscriber<? super Integer> s) {
                Scheduler.Worker inner = Schedulers.newThread().createWorker();
                DisposableSubscription ds = DisposableSubscription.createEmpty(s);
                ds.add(inner);
                s.onSubscribe(ds);
                inner.schedule(() -> {
                        try {
                            for (int i = 0; i < 100; i++) {
                                s.onNext(1);
                                try {
                                    Thread.sleep(1);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        } catch (Exception e) {
                            s.onError(e);
                        }
                        s.onComplete();

                });
            }
        });

        for (int i = 0; i < 10; i++) {
            Flowable<Integer> merge = Flowable.merge(o, o, o);
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            merge.subscribe(ts);

            ts.awaitTerminalEvent();
            assertEquals(1, ts.getCompletions());
            ts.assertValueCount(300);
            //            System.out.println("onNext: " + onNextEvents.size() + " onComplete(): " + ts.getonComplete()Events().size());
        }
    }

    @Test
    public void testConcurrencyWithBrokenOnCompleteContract() {
        Flowable<Integer> o = Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void accept(final Subscriber<? super Integer> s) {
                Scheduler.Worker inner = Schedulers.newThread().createWorker();
                DisposableSubscription ds = DisposableSubscription.createEmpty(s);
                ds.add(inner);
                s.onSubscribe(ds);
                inner.schedule(() -> {
                    try {
                        for (int i = 0; i < 10000; i++) {
                            s.onNext(i);
                        }
                    } catch (Exception e) {
                        s.onError(e);
                    }
                    s.onComplete();
                    s.onComplete();
                    s.onComplete();
                });
            }
        });

        for (int i = 0; i < 10; i++) {
            Flowable<Integer> merge = Flowable.merge(o.onBackpressureBuffer(), o.onBackpressureBuffer(), o.onBackpressureBuffer());
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            merge.subscribe(ts);

            ts.awaitTerminalEvent();
            ts.assertNoErrors();
            ts.assertComplete();
            ts.assertValueCount(30000);
            //                System.out.println("onNext: " + onNextEvents.size() + " onComplete(): " + ts.getonComplete()Events().size());
        }
    }

    @Test
    public void testBackpressureUpstream() throws InterruptedException {
        final AtomicInteger generated1 = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generated1).subscribeOn(Schedulers.computation());
        final AtomicInteger generated2 = new AtomicInteger();
        Flowable<Integer> o2 = createInfiniteFlowable(generated2).subscribeOn(Schedulers.computation());

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                System.err.println("testSubscriber received => " + t + "  on thread " + Thread.currentThread());
                super.onNext(t);
            }
        };

        Flowable.merge(
                o1.take(Flow.defaultBufferSize() * 2), 
                o2.take(Flow.defaultBufferSize() * 2))
                    .subscribe(testSubscriber);
        
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();
        System.err.println(testSubscriber.getValues().size());
        testSubscriber.assertValueCount(Flow.defaultBufferSize() * 4);
        // it should be between the take num and requested batch size across the async boundary
        System.out.println("Generated 1: " + generated1.get());
        System.out.println("Generated 2: " + generated2.get());
        assertTrue(generated1.get() >= Flow.defaultBufferSize() * 2 
                && generated1.get() <= Flow.defaultBufferSize() * 4);
    }

    @Test
    public void testBackpressureUpstream2InLoop() throws InterruptedException {
        for (int i = 0; i < 1000; i++) {
            System.err.flush();
            System.out.println("---");
            System.out.flush();
            testBackpressureUpstream2();
        }
    }
    
    @Test
    public void testBackpressureUpstream2() throws InterruptedException {
        final AtomicInteger generated1 = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generated1).subscribeOn(Schedulers.computation());

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
            }
        };

        Flowable.merge(o1.take(Flow.defaultBufferSize() * 2), Flowable.just(-99)).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        
        List<Integer> onNextEvents = testSubscriber.getValues();
        
        System.out.println("Generated 1: " + generated1.get() + " / received: " + onNextEvents.size());
        System.out.println(onNextEvents);

        testSubscriber.assertNoErrors();
        assertEquals(Flow.defaultBufferSize() * 2 + 1, onNextEvents.size());
        // it should be between the take num and requested batch size across the async boundary
        assertTrue(generated1.get() >= Flow.defaultBufferSize() * 2 && generated1.get() <= Flow.defaultBufferSize() * 3);
    }

    /**
     * This is the same as the upstreams ones, but now adds the downstream as well by using observeOn.
     * 
     * This requires merge to also obey the Product.request values coming from it's child subscriber.
     */
    @Test
    public void testBackpressureDownstreamWithConcurrentStreams() throws InterruptedException {
        final AtomicInteger generated1 = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generated1).subscribeOn(Schedulers.computation());
        final AtomicInteger generated2 = new AtomicInteger();
        Flowable<Integer> o2 = createInfiniteFlowable(generated2).subscribeOn(Schedulers.computation());

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                if (t < 100)
                    try {
                        // force a slow consumer
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                //                System.err.println("testSubscriber received => " + t + "  on thread " + Thread.currentThread());
                super.onNext(t);
            }
        };

        Flowable.merge(o1.take(Flow.defaultBufferSize() * 2), o2.take(Flow.defaultBufferSize() * 2)).observeOn(Schedulers.computation()).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();
        System.err.println(testSubscriber.getValues());
        testSubscriber.assertValueCount(Flow.defaultBufferSize() * 4);
        // it should be between the take num and requested batch size across the async boundary
        System.out.println("Generated 1: " + generated1.get());
        System.out.println("Generated 2: " + generated2.get());
        assertTrue(generated1.get() >= Flow.defaultBufferSize() * 2 && generated1.get() <= Flow.defaultBufferSize() * 4);
    }

    @Test
    public void testBackpressureBothUpstreamAndDownstreamWithSynchronousScalarFlowables() throws InterruptedException {
        final AtomicInteger generated1 = new AtomicInteger();
        Flowable<Flowable<Integer>> o1 = createInfiniteFlowable(generated1)
                .map(Flowable::just);

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                if (t < 100)
                    try {
                        // force a slow consumer
                        Thread.sleep(2);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                //                System.err.println("testSubscriber received => " + t + "  on thread " + Thread.currentThread());
                super.onNext(t);
            }
        };

        Flowable.merge(o1).observeOn(Schedulers.computation()).take(Flow.defaultBufferSize() * 2).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();
        System.out.println("Generated 1: " + generated1.get());
        System.err.println(testSubscriber.getValues());
        testSubscriber.assertValueCount(Flow.defaultBufferSize() * 2);
        // it should be between the take num and requested batch size across the async boundary
        assertTrue(generated1.get() >= Flow.defaultBufferSize() * 2 && generated1.get() <= Flow.defaultBufferSize() * 4);
    }

    /**
     * Currently there is no solution to this ... we can't exert backpressure on the outer Flowable if we
     * can't know if the ones we've received so far are going to emit or not, otherwise we could starve the system.
     * 
     * For example, 10,000 Flowables are being merged (bad use case to begin with, but ...) and it's only one of them
     * that will ever emit. If backpressure only allowed the first 1,000 to be sent, we would hang and never receive an event.
     * 
     * Thus, we must allow all Flowables to be sent. The ScalarSynchronousFlowable use case is an exception to this since
     * we can grab the value synchronously.
     * 
     * @throws InterruptedException
     */
    @Test(timeout = 5000)
    public void testBackpressureBothUpstreamAndDownstreamWithRegularFlowables() throws InterruptedException {
        final AtomicInteger generated1 = new AtomicInteger();
        Flowable<Flowable<Integer>> o1 = createInfiniteFlowable(generated1)
                .map(t1 -> Flowable.just(1, 2, 3));

        TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>() {
            int i = 0;

            @Override
            public void onNext(Integer t) {
                if (i++ < 400)
                    try {
                        // force a slow consumer
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                //                System.err.println("testSubscriber received => " + t + "  on thread " + Thread.currentThread());
                super.onNext(t);
            }
        };

        Flowable.merge(o1).observeOn(Schedulers.computation()).take(Flow.defaultBufferSize() * 2).subscribe(testSubscriber);
        testSubscriber.awaitTerminalEvent();
        testSubscriber.assertNoErrors();
        System.out.println("Generated 1: " + generated1.get());
        System.err.println(testSubscriber.getValues());
        System.out.println("done1 testBackpressureBothUpstreamAndDownstreamWithRegularFlowables ");
        testSubscriber.assertValueCount(Flow.defaultBufferSize() * 2);
        System.out.println("done2 testBackpressureBothUpstreamAndDownstreamWithRegularFlowables ");
        // we can't restrict this ... see comment above
        //        assertTrue(generated1.get() >= Flow.defaultBufferSize() && generated1.get() <= Flow.defaultBufferSize() * 4);
    }

    @Ignore // FIXME null values are forbidden.
    @Test
    public void mergeWithNullValues() {
        System.out.println("mergeWithNullValues");
        TestSubscriber<String> ts = new TestSubscriber<>();
        Flowable.merge(Flowable.just(null, "one"), Flowable.just("two", null)).subscribe(ts);
        ts.assertTerminalEvent();
        ts.assertNoErrors();
        ts.assertValues(null, "one", "two", null);
    }

    @Ignore // FIXME null values are forbidden, Subscription cancellation from producer is no op
    @Test
    public void mergeWithTerminalEventAfterUnsubscribe() {
        System.out.println("mergeWithTerminalEventAfterUnsubscribe");
        TestSubscriber<String> ts = new TestSubscriber<>();
        Flowable<String> bad = Flowable.create(s -> {
                s.onNext("two");
//                s.unsubscribe();
//                s.onComplete();
        });
        Flowable.merge(Flowable.just(null, "one"), bad).subscribe(ts);
        ts.assertNoErrors();
        ts.assertValues(null, "one", "two");
    }

    @Test
    public void mergingNullFlowable() {
        TestSubscriber<String> ts = new TestSubscriber<>();
        Flowable.merge(Flowable.just("one"), null).subscribe(ts);
        ts.assertNoErrors();
        ts.assertValues("one");
    }

    @Test
    public void merge1AsyncStreamOf1() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNAsyncStreamsOfN(1, 1).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(1);
    }

    @Test
    public void merge1AsyncStreamOf1000() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNAsyncStreamsOfN(1, 1000).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(1_000);
    }

    @Test
    public void merge10AsyncStreamOf1000() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNAsyncStreamsOfN(10, 1000).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(10_000);
    }

    @Test
    public void merge1000AsyncStreamOf1000() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNAsyncStreamsOfN(1000, 1000).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(1_000_000);
    }

    @Test
    public void merge2000AsyncStreamOf100() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNAsyncStreamsOfN(2000, 100).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(200_000);
    }

    @Test
    public void merge100AsyncStreamOf1() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNAsyncStreamsOfN(100, 1).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(100);
    }

    private Flowable<Integer> mergeNAsyncStreamsOfN(final int outerSize, final int innerSize) {
        Flowable<Flowable<Integer>> os = Flowable.range(1, outerSize)
                .map(i -> Flowable.range(1, innerSize).subscribeOn(Schedulers.computation()));
        return Flowable.merge(os);
    }

    @Test
    public void merge1SyncStreamOf1() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNSyncStreamsOfN(1, 1).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(1);
    }

    @Test
    public void merge1SyncStreamOf1000000() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNSyncStreamsOfN(1, 1000000).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(1_000_000);
    }

    @Test
    public void merge1000SyncStreamOf1000() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNSyncStreamsOfN(1000, 1000).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(1_000_000);
    }

    @Test
    public void merge10000SyncStreamOf10() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNSyncStreamsOfN(10_000, 10).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(100_000);
    }

    @Test
    public void merge1000000SyncStreamOf1() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        mergeNSyncStreamsOfN(1_000_000, 1).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(1_000_000);
    }

    private Flowable<Integer> mergeNSyncStreamsOfN(final int outerSize, final int innerSize) {
        Flowable<Flowable<Integer>> os = Flowable.range(1, outerSize)
                .map(i -> Flowable.range(1, innerSize));
        return Flowable.merge(os);
    }

    private Flowable<Integer> createInfiniteFlowable(final AtomicInteger generated) {
        Flowable<Integer> observable = Flowable.from(new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {

                    @Override
                    public void remove() {
                    }

                    @Override
                    public Integer next() {
                        return generated.getAndIncrement();
                    }

                    @Override
                    public boolean hasNext() {
                        return true;
                    }
                };
            }
        });
        return observable;
    }

    @Test
    public void mergeManyAsyncSingle() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        Flowable<Flowable<Integer>> os = Flowable.range(1, 10000)
        .map(i -> {
            return Flowable.<Integer>create(s -> {
                AbstractSubscription.setEmptyOn(s);
                if (i < 500) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                s.onNext(i);
                s.onComplete();
            }).subscribeOn(Schedulers.computation()).cache();
        });
        Flowable.merge(os).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(10_000);
    }

    @Test
    public void shouldCompleteAfterApplyingBackpressure_NormalPath() {
        Flowable<Integer> source = Flowable.mergeDelayError(Flowable.just(Flowable.range(1, 2)));
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        subscriber.requestMore(0);
        source.subscribe(subscriber);
        subscriber.requestMore(3); // 1, 2, <complete> - with requestMore(2) we get the 1 and 2 but not the <complete>
        subscriber.assertValues(1, 2);
        subscriber.assertTerminalEvent();
    }

    @Test
    public void shouldCompleteAfterApplyingBackpressure_FastPath() {
        Flowable<Integer> source = Flowable.mergeDelayError(Flowable.just(Flowable.just(1)));
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        subscriber.requestMore(0);
        source.subscribe(subscriber);
        subscriber.requestMore(2); // 1, <complete> - should work as per .._NormalPath above
        subscriber.assertValues(1);
        subscriber.assertTerminalEvent();
    }

    @Test
    public void shouldNotCompleteIfThereArePendingScalarSynchronousEmissionsWhenTheLastInnerSubscriberCompletes() {
        TestScheduler scheduler = Schedulers.test();
        Flowable<Long> source = Flowable.mergeDelayError(Flowable.just(1L), Flowable.timer(1, TimeUnit.SECONDS, scheduler).skip(1));
        TestSubscriber<Long> subscriber = new TestSubscriber<>();
        subscriber.requestMore(0);
        source.subscribe(subscriber);
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        subscriber.assertNoValues();
        subscriber.assertNoComplete();
        subscriber.requestMore(1);
        subscriber.assertValues(1L);

        subscriber.assertNoComplete();
        subscriber.requestMore(1);
        subscriber.assertTerminalEvent();
    }

    @Test
    public void delayedErrorsShouldBeEmittedWhenCompleteAfterApplyingBackpressure_NormalPath() {
        Throwable exception = new Throwable();
        Flowable<Integer> source = Flowable.mergeDelayError(Flowable.range(1, 2), Flowable.<Integer>error(exception));
        TestSubscriber<Integer> subscriber = new TestSubscriber<>(0);
        source.subscribe(subscriber);
        subscriber.requestMore(3); // 1, 2, <error>
        subscriber.assertValues(1, 2);
        subscriber.assertTerminalEvent();
        subscriber.assertError(exception);
    }

    @Test
    public void delayedErrorsShouldBeEmittedWhenCompleteAfterApplyingBackpressure_FastPath() {
        Throwable exception = new Throwable();
        Flowable<Integer> source = Flowable.mergeDelayError(Flowable.just(1), Flowable.<Integer>error(exception));
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        subscriber.requestMore(0);
        source.subscribe(subscriber);
        subscriber.requestMore(2); // 1, <error>
        subscriber.assertValues(1);
        subscriber.assertTerminalEvent();
        subscriber.assertError(exception);
    }

    @Test
    public void shouldNotCompleteWhileThereAreStillScalarSynchronousEmissionsInTheQueue() {
        Flowable<Integer> source = Flowable.merge(Flowable.just(1), Flowable.just(2));
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        subscriber.requestMore(1);
        source.subscribe(subscriber);
        subscriber.assertValues(1);
        subscriber.requestMore(1);
        subscriber.assertValues(1, 2);
    }

    @Test
    public void shouldNotReceivedDelayedErrorWhileThereAreStillScalarSynchronousEmissionsInTheQueue() {
        Throwable exception = new Throwable();
        Flowable<Integer> source = Flowable.mergeDelayError(Flowable.just(1), Flowable.just(2), Flowable.<Integer>error(exception));
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        subscriber.requestMore(1);
        source.subscribe(subscriber);
        subscriber.assertValues(1);
        subscriber.assertNoErrors();
        subscriber.requestMore(1);
        subscriber.assertValues(1, 2);
        subscriber.assertError(exception);
    }

    @Test
    public void shouldNotReceivedDelayedErrorWhileThereAreStillNormalEmissionsInTheQueue() {
        Throwable exception = new Throwable();
        Flowable<Integer> source = Flowable.mergeDelayError(Flowable.range(1, 2), Flowable.range(3, 2), Flowable.<Integer>error(exception));
        TestSubscriber<Integer> subscriber = new TestSubscriber<>();
        subscriber.requestMore(3);
        source.subscribe(subscriber);
        subscriber.assertValues(1, 2, 3);
        subscriber.assertNoErrors();
        subscriber.requestMore(2);
        subscriber.assertValues(1, 2, 3, 4);
        subscriber.assertError(exception);
    }
    
    @Test
    public void testMergeKeepsRequesting() throws InterruptedException {
        //for (int i = 0; i < 5000; i++) {
            //System.out.println(i + ".......................................................................");
            final CountDownLatch latch = new CountDownLatch(1);
            final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();

            Flowable.range(1, 2)
                    // produce many integers per second
                    .flatMap(new Function<Integer, Flowable<Integer>>() {
                        @Override
                        public Flowable<Integer> apply(final Integer number) {
                            return Flowable.range(1, Integer.MAX_VALUE)
                                    .doOnRequest(n -> messages.add(">>>>>>>> A requested[" + number + "]: " + n))
                                    // pause a bit
                                    .doOnNext(pauseForMs(3))
                                    // buffer on backpressure
                                    .onBackpressureBuffer()
                                    // do in parallel
                                    .subscribeOn(Schedulers.computation())
                                    .doOnRequest(n -> messages.add(">>>>>>>> B requested[" + number + "]: " + n));
                        }

                    })
                    // take a number bigger than 2* Flow.defaultBufferSize() (used by OperatorMerge)
                    .take(Flow.defaultBufferSize() * 2 + 1)
                    // log count
                    .doOnNext(printCount())
                    // release latch
                    .doOnComplete(() -> latch.countDown()).subscribe();
            boolean a = latch.await(2, TimeUnit.SECONDS);
            if (!a) {
                for (String s : messages) {
                    System.out.println("DEBUG => " + s);
                }
            }
            assertTrue(a);
        //}
    }
    
    @Test
    public void testMergeRequestOverflow() throws InterruptedException {
        //do a non-trivial merge so that future optimisations with EMPTY don't invalidate this test
        Flowable<Integer> o = Flowable.from(Arrays.asList(1,2)).mergeWith(Flowable.from(Arrays.asList(3,4)));
        final int expectedCount = 4;
        final CountDownLatch latch = new CountDownLatch(expectedCount);
        o.subscribeOn(Schedulers.computation()).subscribe(new AbstractSubscriber<Integer>() {
            
            @Override
            public void onSubscribe() {
                subscription.request(1);
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
                subscription.request(2);
                subscription.request(Long.MAX_VALUE-1);
            }});
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    private static Consumer<Integer> printCount() {
        return new Consumer<Integer>() {
            long count;

            @Override
            public void accept(Integer t1) {
                count++;
                System.out.println("count=" + count);
            }
        };
    }

    private static Consumer<Integer> pauseForMs(final long time) {
        return new Consumer<Integer>() {
            @Override
            public void accept(Integer s) {
                try {
                    Thread.sleep(time);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
