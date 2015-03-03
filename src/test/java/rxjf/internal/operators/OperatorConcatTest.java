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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.internal.util.RxRingBuffer;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subscriptions.BooleanSubscription;

public class OperatorConcatTest {

    @Test
    public void testConcat() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };

        final Flowable<String> odds = Flowable.from(o);
        final Flowable<String> even = Flowable.from(e);

        Flowable<String> concat = Flowable.concat(odds, even);
        concat.subscribe(observer);

        verify(observer, times(7)).onNext(anyString());
    }

    @Test
    public void testConcatWithList() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };

        final Flowable<String> odds = Flowable.from(o);
        final Flowable<String> even = Flowable.from(e);
        final List<Flowable<String>> list = new ArrayList<Flowable<String>>();
        list.add(odds);
        list.add(even);
        Flowable<String> concat = Flowable.concat(Flowable.from(list));
        concat.subscribe(observer);

        verify(observer, times(7)).onNext(anyString());
    }

    @Test
    public void testConcatFlowableOfFlowables() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };

        final Flowable<String> odds = Flowable.from(o);
        final Flowable<String> even = Flowable.from(e);

        Flowable<Flowable<String>> observableOfFlowables = Flowable.create(new Flowable.OnSubscribe<Flowable<String>>() {

            @Override
            public void call(Subscriber<? super Flowable<String>> observer) {
                // simulate what would happen in an observable
                observer.onNext(odds);
                observer.onNext(even);
                observer.onComplete();
            }

        });
        Flowable<String> concat = Flowable.concat(observableOfFlowables);

        concat.subscribe(observer);

        verify(observer, times(7)).onNext(anyString());
    }

    /**
     * Simple concat of 2 asynchronous observables ensuring it emits in correct order.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSimpleAsyncConcat() {
        Observer<String> observer = mock(Observer.class);

        TestFlowable<String> o1 = new TestFlowable<String>("one", "two", "three");
        TestFlowable<String> o2 = new TestFlowable<String>("four", "five", "six");

        Flowable.concat(Flowable.create(o1), Flowable.create(o2)).subscribe(observer);

        try {
            // wait for async observables to complete
            o1.t.join();
            o2.t.join();
        } catch (Throwable e) {
            throw new RuntimeException("failed waiting on threads");
        }

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(1)).onNext("four");
        inOrder.verify(observer, times(1)).onNext("five");
        inOrder.verify(observer, times(1)).onNext("six");
    }

    /**
     * Test an async Flowable that emits more async Flowables
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testNestedAsyncConcat() throws Throwable {
        Observer<String> observer = mock(Observer.class);

        final TestFlowable<String> o1 = new TestFlowable<String>("one", "two", "three");
        final TestFlowable<String> o2 = new TestFlowable<String>("four", "five", "six");
        final TestFlowable<String> o3 = new TestFlowable<String>("seven", "eight", "nine");
        final CountDownLatch allowThird = new CountDownLatch(1);

        final AtomicReference<Thread> parent = new AtomicReference<Thread>();
        final CountDownLatch parentHasStarted = new CountDownLatch(1);
        Flowable<Flowable<String>> observableOfFlowables = Flowable.create(new Flowable.OnSubscribe<Flowable<String>>() {

            @Override
            public void call(final Subscriber<? super Flowable<String>> observer) {
                final BooleanSubscription s = new BooleanSubscription();
                observer.add(s);
                parent.set(new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            // emit first
                            if (!s.isUnsubscribed()) {
                                System.out.println("Emit o1");
                                observer.onNext(Flowable.create(o1));
                            }
                            // emit second
                            if (!s.isUnsubscribed()) {
                                System.out.println("Emit o2");
                                observer.onNext(Flowable.create(o2));
                            }

                            // wait until sometime later and emit third
                            try {
                                allowThird.await();
                            } catch (InterruptedException e) {
                                observer.onError(e);
                            }
                            if (!s.isUnsubscribed()) {
                                System.out.println("Emit o3");
                                observer.onNext(Flowable.create(o3));
                            }

                        } catch (Throwable e) {
                            observer.onError(e);
                        } finally {
                            System.out.println("Done parent Flowable");
                            observer.onComplete();
                        }
                    }
                }));
                parent.get().start();
                parentHasStarted.countDown();
            }
        });

        Flowable.concat(observableOfFlowables).subscribe(observer);

        // wait for parent to start
        parentHasStarted.await();

        try {
            // wait for first 2 async observables to complete
            System.out.println("Thread1 is starting ... waiting for it to complete ...");
            o1.waitForThreadDone();
            System.out.println("Thread2 is starting ... waiting for it to complete ...");
            o2.waitForThreadDone();
        } catch (Throwable e) {
            throw new RuntimeException("failed waiting on threads", e);
        }

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(1)).onNext("four");
        inOrder.verify(observer, times(1)).onNext("five");
        inOrder.verify(observer, times(1)).onNext("six");
        // we shouldn't have the following 3 yet
        inOrder.verify(observer, never()).onNext("seven");
        inOrder.verify(observer, never()).onNext("eight");
        inOrder.verify(observer, never()).onNext("nine");
        // we should not be completed yet
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        // now allow the third
        allowThird.countDown();

        try {
            // wait for 3rd to complete
            o3.waitForThreadDone();
        } catch (Throwable e) {
            throw new RuntimeException("failed waiting on threads", e);
        }

        inOrder.verify(observer, times(1)).onNext("seven");
        inOrder.verify(observer, times(1)).onNext("eight");
        inOrder.verify(observer, times(1)).onNext("nine");

        inOrder.verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockedFlowableOfFlowables() {
        Observer<String> observer = mock(Observer.class);

        final String[] o = { "1", "3", "5", "7" };
        final String[] e = { "2", "4", "6" };
        final Flowable<String> odds = Flowable.from(o);
        final Flowable<String> even = Flowable.from(e);
        final CountDownLatch callOnce = new CountDownLatch(1);
        final CountDownLatch okToContinue = new CountDownLatch(1);
        TestFlowable<Flowable<String>> observableOfFlowables = new TestFlowable<Flowable<String>>(callOnce, okToContinue, odds, even);
        Flowable<String> concatF = Flowable.concat(Flowable.create(observableOfFlowables));
        concatF.subscribe(observer);
        try {
            //Block main thread to allow observables to serve up o1.
            callOnce.await();
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        // The concated observable should have served up all of the odds.
        verify(observer, times(1)).onNext("1");
        verify(observer, times(1)).onNext("3");
        verify(observer, times(1)).onNext("5");
        verify(observer, times(1)).onNext("7");

        try {
            // unblock observables so it can serve up o2 and complete
            okToContinue.countDown();
            observableOfFlowables.t.join();
        } catch (Throwable ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        // The concatenated observable should now have served up all the evens.
        verify(observer, times(1)).onNext("2");
        verify(observer, times(1)).onNext("4");
        verify(observer, times(1)).onNext("6");
    }

    @Test
    public void testConcatConcurrentWithInfinity() {
        final TestFlowable<String> w1 = new TestFlowable<String>("one", "two", "three");
        //This observable will send "hello" MAX_VALUE time.
        final TestFlowable<String> w2 = new TestFlowable<String>("hello", Integer.MAX_VALUE);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        @SuppressWarnings("unchecked")
        TestFlowable<Flowable<String>> observableOfFlowables = new TestFlowable<Flowable<String>>(Flowable.create(w1), Flowable.create(w2));
        Flowable<String> concatF = Flowable.concat(Flowable.create(observableOfFlowables));

        concatF.take(50).subscribe(observer);

        //Wait for the thread to start up.
        try {
            w1.waitForThreadDone();
            w2.waitForThreadDone();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(47)).onNext("hello");
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testConcatNonBlockingFlowables() {

        final CountDownLatch okToContinueW1 = new CountDownLatch(1);
        final CountDownLatch okToContinueW2 = new CountDownLatch(1);

        final TestFlowable<String> w1 = new TestFlowable<String>(null, okToContinueW1, "one", "two", "three");
        final TestFlowable<String> w2 = new TestFlowable<String>(null, okToContinueW2, "four", "five", "six");

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Flowable<Flowable<String>> observableOfFlowables = Flowable.create(new Flowable.OnSubscribe<Flowable<String>>() {

            @Override
            public void call(Subscriber<? super Flowable<String>> observer) {
                // simulate what would happen in an observable
                observer.onNext(Flowable.create(w1));
                observer.onNext(Flowable.create(w2));
                observer.onComplete();
            }

        });
        Flowable<String> concat = Flowable.concat(observableOfFlowables);
        concat.subscribe(observer);

        verify(observer, times(0)).onComplete();

        try {
            // release both threads
            okToContinueW1.countDown();
            okToContinueW2.countDown();
            // wait for both to finish
            w1.t.join();
            w2.t.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(1)).onNext("four");
        inOrder.verify(observer, times(1)).onNext("five");
        inOrder.verify(observer, times(1)).onNext("six");
        verify(observer, times(1)).onComplete();

    }

    /**
     * Test unsubscribing the concatenated Flowable in a single thread.
     */
    @Test
    public void testConcatUnsubscribe() {
        final CountDownLatch callOnce = new CountDownLatch(1);
        final CountDownLatch okToContinue = new CountDownLatch(1);
        final TestFlowable<String> w1 = new TestFlowable<String>("one", "two", "three");
        final TestFlowable<String> w2 = new TestFlowable<String>(callOnce, okToContinue, "four", "five", "six");

        @SuppressWarnings("unchecked")
        final Observer<String> observer = mock(Observer.class);

        final Flowable<String> concat = Flowable.concat(Flowable.create(w1), Flowable.create(w2));

        try {
            // Subscribe
            Subscription s1 = concat.subscribe(observer);
            //Block main thread to allow observable "w1" to complete and observable "w2" to call onNext once.
            callOnce.await();
            // Unsubcribe
            s1.unsubscribe();
            //Unblock the observable to continue.
            okToContinue.countDown();
            w1.t.join();
            w2.t.join();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(1)).onNext("four");
        inOrder.verify(observer, never()).onNext("five");
        inOrder.verify(observer, never()).onNext("six");
        inOrder.verify(observer, never()).onComplete();

    }

    /**
     * All observables will be running in different threads so subscribe() is unblocked. CountDownLatch is only used in order to call unsubscribe() in a predictable manner.
     */
    @Test
    public void testConcatUnsubscribeConcurrent() {
        final CountDownLatch callOnce = new CountDownLatch(1);
        final CountDownLatch okToContinue = new CountDownLatch(1);
        final TestFlowable<String> w1 = new TestFlowable<String>("one", "two", "three");
        final TestFlowable<String> w2 = new TestFlowable<String>(callOnce, okToContinue, "four", "five", "six");

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        @SuppressWarnings("unchecked")
        TestFlowable<Flowable<String>> observableOfFlowables = new TestFlowable<Flowable<String>>(Flowable.create(w1), Flowable.create(w2));
        Flowable<String> concatF = Flowable.concat(Flowable.create(observableOfFlowables));

        Subscription s1 = concatF.subscribe(observer);

        try {
            //Block main thread to allow observable "w1" to complete and observable "w2" to call onNext exactly once.
            callOnce.await();
            //"four" from w2 has been processed by onNext()
            s1.unsubscribe();
            //"five" and "six" will NOT be processed by onNext()
            //Unblock the observable to continue.
            okToContinue.countDown();
            w1.t.join();
            w2.t.join();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("one");
        inOrder.verify(observer, times(1)).onNext("two");
        inOrder.verify(observer, times(1)).onNext("three");
        inOrder.verify(observer, times(1)).onNext("four");
        inOrder.verify(observer, never()).onNext("five");
        inOrder.verify(observer, never()).onNext("six");
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    private static class TestFlowable<T> implements Flowable.OnSubscribe<T> {

        private final Subscription s = new Subscription() {

            @Override
            public void unsubscribe() {
                subscribed = false;
            }

            @Override
            public boolean isUnsubscribed() {
                return !subscribed;
            }

        };
        private final List<T> values;
        private Thread t = null;
        private int count = 0;
        private boolean subscribed = true;
        private final CountDownLatch once;
        private final CountDownLatch okToContinue;
        private final CountDownLatch threadHasStarted = new CountDownLatch(1);
        private final T seed;
        private final int size;

        public TestFlowable(T... values) {
            this(null, null, values);
        }

        public TestFlowable(CountDownLatch once, CountDownLatch okToContinue, T... values) {
            this.values = Arrays.asList(values);
            this.size = this.values.size();
            this.once = once;
            this.okToContinue = okToContinue;
            this.seed = null;
        }

        public TestFlowable(T seed, int size) {
            values = null;
            once = null;
            okToContinue = null;
            this.seed = seed;
            this.size = size;
        }

        @Override
        public void call(final Subscriber<? super T> observer) {
            observer.add(s);
            t = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        while (count < size && subscribed) {
                            if (null != values)
                                observer.onNext(values.get(count));
                            else
                                observer.onNext(seed);
                            count++;
                            //Unblock the main thread to call unsubscribe.
                            if (null != once)
                                once.countDown();
                            //Block until the main thread has called unsubscribe.
                            if (null != okToContinue)
                                okToContinue.await(5, TimeUnit.SECONDS);
                        }
                        if (subscribed)
                            observer.onComplete();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        fail(e.getMessage());
                    }
                }

            });
            t.start();
            threadHasStarted.countDown();
        }

        void waitForThreadDone() throws InterruptedException {
            threadHasStarted.await();
            t.join();
        }
    }

    @Test
    public void testMultipleObservers() {
        @SuppressWarnings("unchecked")
        Observer<Object> o1 = mock(Observer.class);
        @SuppressWarnings("unchecked")
        Observer<Object> o2 = mock(Observer.class);

        TestScheduler s = new TestScheduler();

        Flowable<Long> timer = Flowable.interval(500, TimeUnit.MILLISECONDS, s).take(2);
        Flowable<Long> o = Flowable.concat(timer, timer);

        o.subscribe(o1);
        o.subscribe(o2);

        InOrder inOrder1 = inOrder(o1);
        InOrder inOrder2 = inOrder(o2);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(0L);
        inOrder2.verify(o2, times(1)).onNext(0L);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(1L);
        inOrder2.verify(o2, times(1)).onNext(1L);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(0L);
        inOrder2.verify(o2, times(1)).onNext(0L);

        s.advanceTimeBy(500, TimeUnit.MILLISECONDS);

        inOrder1.verify(o1, times(1)).onNext(1L);
        inOrder2.verify(o2, times(1)).onNext(1L);

        inOrder1.verify(o1, times(1)).onComplete();
        inOrder2.verify(o2, times(1)).onComplete();

        verify(o1, never()).onError(any(Throwable.class));
        verify(o2, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void concatVeryLongFlowableOfFlowables() {
        final int n = 10000;
        Flowable<Flowable<Integer>> source = Flowable.create(new OnSubscribe<Flowable<Integer>>() {
            @Override
            public void call(Subscriber<? super Flowable<Integer>> s) {
                for (int i = 0; i < n; i++) {
                    if (s.isUnsubscribed()) {
                        return;
                    }
                    s.onNext(Flowable.just(i));
                }
                s.onComplete();
            }
        });
        
        Flowable<List<Integer>> result = Flowable.concat(source).toList();
        
        @SuppressWarnings("unchecked")
        Observer<List<Integer>> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        result.subscribe(o);

        List<Integer> list = new ArrayList<Integer>(n);
        for (int i = 0; i < n; i++) {
            list.add(i);
        }
        inOrder.verify(o).onNext(list);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    @Test
    public void concatVeryLongFlowableOfFlowablesTakeHalf() {
        final int n = 10000;
        Flowable<Flowable<Integer>> source = Flowable.create(new OnSubscribe<Flowable<Integer>>() {
            @Override
            public void call(Subscriber<? super Flowable<Integer>> s) {
                for (int i = 0; i < n; i++) {
                    if (s.isUnsubscribed()) {
                        return;
                    }
                    s.onNext(Flowable.just(i));
                }
                s.onComplete();
            }
        });
        
        Flowable<List<Integer>> result = Flowable.concat(source).take(n / 2).toList();
        
        @SuppressWarnings("unchecked")
        Observer<List<Integer>> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        result.subscribe(o);

        List<Integer> list = new ArrayList<Integer>(n);
        for (int i = 0; i < n / 2; i++) {
            list.add(i);
        }
        inOrder.verify(o).onNext(list);
        inOrder.verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void testConcatOuterBackpressure() {
        assertEquals(1,
                (int) Flowable.<Integer> empty()
                        .concatWith(Flowable.just(1))
                        .take(1)
                        .toBlocking().single());
    }
    
    @Test
    public void testInnerBackpressureWithAlignedBoundaries() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(0, RxRingBuffer.SIZE * 2)
                .concatWith(Flowable.range(0, RxRingBuffer.SIZE * 2))
                .observeOn(Schedulers.computation()) // observeOn has a backpressured RxRingBuffer
                .subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(RxRingBuffer.SIZE * 4, ts.getOnNextEvents().size());
    }

    /*
     * Testing without counts aligned with buffer sizes because concat must prevent the subscription
     * to the next Flowable if request == 0 which can happen at the end of a subscription
     * if the request size == emitted size. It needs to delay subscription until the next request when aligned, 
     * when not aligned, it just subscribesNext with the outstanding request amount.
     */
    @Test
    public void testInnerBackpressureWithoutAlignedBoundaries() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(0, (RxRingBuffer.SIZE * 2) + 10)
                .concatWith(Flowable.range(0, (RxRingBuffer.SIZE * 2) + 10))
                .observeOn(Schedulers.computation()) // observeOn has a backpressured RxRingBuffer
                .subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals((RxRingBuffer.SIZE * 4) + 20, ts.getOnNextEvents().size());
    }
    
    // https://github.com/ReactiveX/RxJava/issues/1818
    @Test
    public void testConcatWithNonCompliantSourceDoubleOnComplete() {
        Flowable<String> o = Flowable.create(new OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> s) {
                s.onNext("hello");
                s.onComplete();
                s.onComplete();
            }
            
        });
        
        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.concat(o, o).subscribe(ts);
        ts.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
        ts.assertNoErrors();
        ts.assertReceivedOnNext(Arrays.asList("hello", "hello"));
    }

}
