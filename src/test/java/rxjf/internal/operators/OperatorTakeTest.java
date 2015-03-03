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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Producer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Function;
import rx.observers.Subscribers;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class OperatorTakeTest {

    @Test
    public void testTake1() {
        Flowable<String> w = Flowable.from(Arrays.asList("one", "two", "three"));
        Flowable<String> take = w.lift(new OperatorTake<String>(2));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        take.subscribe(observer);
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, never()).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testTake2() {
        Flowable<String> w = Flowable.from(Arrays.asList("one", "two", "three"));
        Flowable<String> take = w.lift(new OperatorTake<String>(1));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        take.subscribe(observer);
        verify(observer, times(1)).onNext("one");
        verify(observer, never()).onNext("two");
        verify(observer, never()).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTakeWithError() {
        Flowable.from(Arrays.asList(1, 2, 3)).take(1).map(new Function<Integer, Integer>() {
            @Override
            public Integer call(Integer t1) {
                throw new IllegalArgumentException("some error");
            }
        }).toBlocking().single();
    }

    @Test
    public void testTakeWithErrorHappeningInOnNext() {
        Flowable<Integer> w = Flowable.from(Arrays.asList(1, 2, 3)).take(2).map(new Function<Integer, Integer>() {
            @Override
            public Integer call(Integer t1) {
                throw new IllegalArgumentException("some error");
            }
        });

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        w.subscribe(observer);
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(any(IllegalArgumentException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTakeWithErrorHappeningInTheLastOnNext() {
        Flowable<Integer> w = Flowable.from(Arrays.asList(1, 2, 3)).take(1).map(new Function<Integer, Integer>() {
            @Override
            public Integer call(Integer t1) {
                throw new IllegalArgumentException("some error");
            }
        });

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        w.subscribe(observer);
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(any(IllegalArgumentException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testTakeDoesntLeakErrors() {
        Flowable<String> source = Flowable.create(new Flowable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> observer) {
                observer.onNext("one");
                observer.onError(new Throwable("test failed"));
            }
        });

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);

        source.lift(new OperatorTake<String>(1)).subscribe(observer);

        verify(observer, times(1)).onNext("one");
        // even though onError is called we take(1) so shouldn't see it
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testTakeZeroDoesntLeakError() {
        final AtomicBoolean subscribed = new AtomicBoolean(false);
        final AtomicBoolean unSubscribed = new AtomicBoolean(false);
        Flowable<String> source = Flowable.create(new Flowable.OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> observer) {
                subscribed.set(true);
                observer.add(new Subscription() {
                    @Override
                    public void unsubscribe() {
                        unSubscribed.set(true);
                    }

                    @Override
                    public boolean isUnsubscribed() {
                        return unSubscribed.get();
                    }
                });
                observer.onError(new Throwable("test failed"));
            }
        });

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);

        source.lift(new OperatorTake<String>(0)).subscribe(observer);
        assertTrue("source subscribed", subscribed.get());
        assertTrue("source unsubscribed", unSubscribed.get());

        verify(observer, never()).onNext(anyString());
        // even though onError is called we take(0) so shouldn't see it
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testUnsubscribeAfterTake() {
        final Subscription s = mock(Subscription.class);
        TestFlowableFunc f = new TestFlowableFunc("one", "two", "three");
        Flowable<String> w = Flowable.create(f);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        
        Subscriber<String> subscriber = Subscribers.from(observer);
        subscriber.add(s);
        
        Flowable<String> take = w.lift(new OperatorTake<String>(1));
        take.subscribe(subscriber);

        // wait for the Flowable to complete
        try {
            f.t.join();
        } catch (Throwable e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

        System.out.println("TestFlowable thread finished");
        verify(observer, times(1)).onNext("one");
        verify(observer, never()).onNext("two");
        verify(observer, never()).onNext("three");
        verify(observer, times(1)).onComplete();
        verify(s, times(1)).unsubscribe();
        verifyNoMoreInteractions(observer);
    }

    @Test(timeout = 2000)
    public void testUnsubscribeFromSynchronousInfiniteFlowable() {
        final AtomicLong count = new AtomicLong();
        INFINITE_OBSERVABLE.take(10).subscribe(new Action1<Long>() {

            @Override
            public void call(Long l) {
                count.set(l);
            }

        });
        assertEquals(10, count.get());
    }

    @Test(timeout = 2000)
    public void testMultiTake() {
        final AtomicInteger count = new AtomicInteger();
        Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(Subscriber<? super Integer> s) {
                for (int i = 0; !s.isUnsubscribed(); i++) {
                    System.out.println("Emit: " + i);
                    count.incrementAndGet();
                    s.onNext(i);
                }
            }

        }).take(100).take(1).toBlocking().forEach(new Action1<Integer>() {

            @Override
            public void call(Integer t1) {
                System.out.println("Receive: " + t1);

            }

        });

        assertEquals(1, count.get());
    }

    private static class TestFlowableFunc implements Flowable.OnSubscribe<String> {

        final String[] values;
        Thread t = null;

        public TestFlowableFunc(String... values) {
            this.values = values;
        }

        @Override
        public void call(final Subscriber<? super String> observer) {
            System.out.println("TestFlowable subscribed to ...");
            t = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        System.out.println("running TestFlowable thread");
                        for (String s : values) {
                            System.out.println("TestFlowable onNext: " + s);
                            observer.onNext(s);
                        }
                        observer.onComplete();
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }

            });
            System.out.println("starting TestFlowable thread");
            t.start();
            System.out.println("done starting TestFlowable thread");
        }
    }

    private static Flowable<Long> INFINITE_OBSERVABLE = Flowable.create(new OnSubscribe<Long>() {

        @Override
        public void call(Subscriber<? super Long> op) {
            long l = 1;
            while (!op.isUnsubscribed()) {
                op.onNext(l++);
            }
            op.onComplete();
        }

    });
    
    @Test(timeout = 2000)
    public void testTakeObserveOn() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        TestSubscriber<Object> ts = new TestSubscriber<Object>(o);
        
        INFINITE_OBSERVABLE.onBackpressureDrop().observeOn(Schedulers.newThread()).take(1).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        
        verify(o).onNext(1L);
        verify(o, never()).onNext(2L);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void testProducerRequestThroughTake() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        ts.requestMore(3);
        final AtomicLong requested = new AtomicLong();
        Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(Subscriber<? super Integer> s) {
                s.setProducer(new Producer() {

                    @Override
                    public void request(long n) {
                        requested.set(n);
                    }

                });
            }

        }).take(3).subscribe(ts);
        assertEquals(3, requested.get());
    }
    
    @Test
    public void testProducerRequestThroughTakeIsModified() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        ts.requestMore(3);
        final AtomicLong requested = new AtomicLong();
        Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(Subscriber<? super Integer> s) {
                s.setProducer(new Producer() {

                    @Override
                    public void request(long n) {
                        requested.set(n);
                    }

                });
            }

        }).take(1).subscribe(ts);
        assertEquals(1, requested.get());
    }
    
    @Test
    public void testInterrupt() throws InterruptedException {
        final AtomicReference<Object> exception = new AtomicReference<Object>();
        final CountDownLatch latch = new CountDownLatch(1);
        Flowable.just(1).subscribeOn(Schedulers.computation()).take(1).subscribe(new Action1<Integer>() {

            @Override
            public void call(Integer t1) {
                try {
                    Thread.sleep(100);
                } catch (Exception e) {
                    exception.set(e);
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            }

        });

        latch.await();
        assertNull(exception.get());
    }
}
