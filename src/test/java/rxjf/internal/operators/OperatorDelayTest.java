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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;

import rx.Notification;
import rx.Flowable;
import rx.Observer;
import rx.Subscription;
import rx.exceptions.TestException;
import rx.functions.Action1;
import rx.functions.Supplier;
import rx.functions.Function;
import rx.internal.util.RxRingBuffer;
import rx.observers.TestObserver;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

public class OperatorDelayTest {
    @Mock
    private Observer<Long> observer;
    @Mock
    private Observer<Long> observer2;

    private TestScheduler scheduler;

    @Before
    public void before() {
        initMocks(this);
        scheduler = new TestScheduler();
    }

    @Test
    public void testDelay() {
        Flowable<Long> source = Flowable.interval(1L, TimeUnit.SECONDS, scheduler).take(3);
        Flowable<Long> delayed = source.delay(500L, TimeUnit.MILLISECONDS, scheduler);
        delayed.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        scheduler.advanceTimeTo(1499L, TimeUnit.MILLISECONDS);
        verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(1500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(0L);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(2400L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(2500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(1L);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(3400L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(3500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(2L);
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testLongDelay() {
        Flowable<Long> source = Flowable.interval(1L, TimeUnit.SECONDS, scheduler).take(3);
        Flowable<Long> delayed = source.delay(5L, TimeUnit.SECONDS, scheduler);
        delayed.subscribe(observer);

        InOrder inOrder = inOrder(observer);

        scheduler.advanceTimeTo(5999L, TimeUnit.MILLISECONDS);
        verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(6000L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(0L);
        scheduler.advanceTimeTo(6999L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        scheduler.advanceTimeTo(7000L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(1L);
        scheduler.advanceTimeTo(7999L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        scheduler.advanceTimeTo(8000L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(2L);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verify(observer, never()).onNext(anyLong());
        inOrder.verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelayWithError() {
        Flowable<Long> source = Flowable.interval(1L, TimeUnit.SECONDS, scheduler).map(new Function<Long, Long>() {
            @Override
            public Long call(Long value) {
                if (value == 1L) {
                    throw new RuntimeException("error!");
                }
                return value;
            }
        });
        Flowable<Long> delayed = source.delay(1L, TimeUnit.SECONDS, scheduler);
        delayed.subscribe(observer);

        InOrder inOrder = inOrder(observer);

        scheduler.advanceTimeTo(1999L, TimeUnit.MILLISECONDS);
        verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(2000L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();

        scheduler.advanceTimeTo(5000L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
    }

    @Test
    public void testDelayWithMultipleSubscriptions() {
        Flowable<Long> source = Flowable.interval(1L, TimeUnit.SECONDS, scheduler).take(3);
        Flowable<Long> delayed = source.delay(500L, TimeUnit.MILLISECONDS, scheduler);
        delayed.subscribe(observer);
        delayed.subscribe(observer2);

        InOrder inOrder = inOrder(observer);
        InOrder inOrder2 = inOrder(observer2);

        scheduler.advanceTimeTo(1499L, TimeUnit.MILLISECONDS);
        verify(observer, never()).onNext(anyLong());
        verify(observer2, never()).onNext(anyLong());

        scheduler.advanceTimeTo(1500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(0L);
        inOrder2.verify(observer2, times(1)).onNext(0L);

        scheduler.advanceTimeTo(2499L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        inOrder2.verify(observer2, never()).onNext(anyLong());

        scheduler.advanceTimeTo(2500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(1L);
        inOrder2.verify(observer2, times(1)).onNext(1L);

        verify(observer, never()).onComplete();
        verify(observer2, never()).onComplete();

        scheduler.advanceTimeTo(3500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(2L);
        inOrder2.verify(observer2, times(1)).onNext(2L);
        inOrder.verify(observer, never()).onNext(anyLong());
        inOrder2.verify(observer2, never()).onNext(anyLong());
        inOrder.verify(observer, times(1)).onComplete();
        inOrder2.verify(observer2, times(1)).onComplete();

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer2, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelaySubscription() {
        Flowable<Integer> result = Flowable.just(1, 2, 3).delaySubscription(100, TimeUnit.MILLISECONDS, scheduler);

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        result.subscribe(o);

        inOrder.verify(o, never()).onNext(any());
        inOrder.verify(o, never()).onComplete();

        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);

        inOrder.verify(o, times(1)).onNext(1);
        inOrder.verify(o, times(1)).onNext(2);
        inOrder.verify(o, times(1)).onNext(3);
        inOrder.verify(o, times(1)).onComplete();

        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelaySubscriptionCancelBeforeTime() {
        Flowable<Integer> result = Flowable.just(1, 2, 3).delaySubscription(100, TimeUnit.MILLISECONDS, scheduler);

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        Subscription s = result.subscribe(o);
        s.unsubscribe();
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);

        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelayWithFlowableNormal1() {
        PublishSubject<Integer> source = PublishSubject.create();
        final List<PublishSubject<Integer>> delays = new ArrayList<PublishSubject<Integer>>();
        final int n = 10;
        for (int i = 0; i < n; i++) {
            PublishSubject<Integer> delay = PublishSubject.create();
            delays.add(delay);
        }

        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Integer t1) {
                return delays.get(t1);
            }
        };

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(delayFunc).subscribe(o);

        for (int i = 0; i < n; i++) {
            source.onNext(i);
            delays.get(i).onNext(i);
            inOrder.verify(o).onNext(i);
        }
        source.onComplete();

        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();

        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelayWithFlowableSingleSend1() {
        PublishSubject<Integer> source = PublishSubject.create();
        final PublishSubject<Integer> delay = PublishSubject.create();

        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return delay;
            }
        };
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(delayFunc).subscribe(o);

        source.onNext(1);
        delay.onNext(1);
        delay.onNext(2);

        inOrder.verify(o).onNext(1);
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelayWithFlowableSourceThrows() {
        PublishSubject<Integer> source = PublishSubject.create();
        final PublishSubject<Integer> delay = PublishSubject.create();

        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return delay;
            }
        };
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(delayFunc).subscribe(o);
        source.onNext(1);
        source.onError(new TestException());
        delay.onNext(1);

        inOrder.verify(o).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testDelayWithFlowableDelayFunctionThrows() {
        PublishSubject<Integer> source = PublishSubject.create();

        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                throw new TestException();
            }
        };
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(delayFunc).subscribe(o);
        source.onNext(1);

        inOrder.verify(o).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testDelayWithFlowableDelayThrows() {
        PublishSubject<Integer> source = PublishSubject.create();
        final PublishSubject<Integer> delay = PublishSubject.create();

        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return delay;
            }
        };
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(delayFunc).subscribe(o);
        source.onNext(1);
        delay.onError(new TestException());

        inOrder.verify(o).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testDelayWithFlowableSubscriptionNormal() {
        PublishSubject<Integer> source = PublishSubject.create();
        final PublishSubject<Integer> delay = PublishSubject.create();
        Supplier<Flowable<Integer>> subFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call() {
                return delay;
            }
        };
        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return delay;
            }
        };

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(subFunc, delayFunc).subscribe(o);

        source.onNext(1);
        delay.onNext(1);

        source.onNext(2);
        delay.onNext(2);

        inOrder.verify(o).onNext(2);
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onError(any(Throwable.class));
        verify(o, never()).onComplete();
    }

    @Test
    public void testDelayWithFlowableSubscriptionFunctionThrows() {
        PublishSubject<Integer> source = PublishSubject.create();
        final PublishSubject<Integer> delay = PublishSubject.create();
        Supplier<Flowable<Integer>> subFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call() {
                throw new TestException();
            }
        };
        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return delay;
            }
        };

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(subFunc, delayFunc).subscribe(o);

        source.onNext(1);
        delay.onNext(1);

        source.onNext(2);

        inOrder.verify(o).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testDelayWithFlowableSubscriptionThrows() {
        PublishSubject<Integer> source = PublishSubject.create();
        final PublishSubject<Integer> delay = PublishSubject.create();
        Supplier<Flowable<Integer>> subFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call() {
                return delay;
            }
        };
        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return delay;
            }
        };

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(subFunc, delayFunc).subscribe(o);

        source.onNext(1);
        delay.onError(new TestException());

        source.onNext(2);

        inOrder.verify(o).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testDelayWithFlowableEmptyDelayer() {
        PublishSubject<Integer> source = PublishSubject.create();

        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return Flowable.empty();
            }
        };
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(delayFunc).subscribe(o);

        source.onNext(1);
        source.onComplete();

        inOrder.verify(o).onNext(1);
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelayWithFlowableSubscriptionRunCompletion() {
        PublishSubject<Integer> source = PublishSubject.create();
        final PublishSubject<Integer> sdelay = PublishSubject.create();
        final PublishSubject<Integer> delay = PublishSubject.create();
        Supplier<Flowable<Integer>> subFunc = new Supplier<Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call() {
                return sdelay;
            }
        };
        Function<Integer, Flowable<Integer>> delayFunc = new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return delay;
            }
        };

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        source.delay(subFunc, delayFunc).subscribe(o);

        source.onNext(1);
        sdelay.onComplete();

        source.onNext(2);
        delay.onNext(2);

        inOrder.verify(o).onNext(2);
        inOrder.verifyNoMoreInteractions();
        verify(o, never()).onError(any(Throwable.class));
        verify(o, never()).onComplete();
    }

    @Test
    public void testDelayWithFlowableAsTimed() {
        Flowable<Long> source = Flowable.interval(1L, TimeUnit.SECONDS, scheduler).take(3);

        final Flowable<Long> delayer = Flowable.timer(500L, TimeUnit.MILLISECONDS, scheduler);

        Function<Long, Flowable<Long>> delayFunc = new Function<Long, Flowable<Long>>() {
            @Override
            public Flowable<Long> call(Long t1) {
                return delayer;
            }
        };

        Flowable<Long> delayed = source.delay(delayFunc);
        delayed.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        scheduler.advanceTimeTo(1499L, TimeUnit.MILLISECONDS);
        verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(1500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(0L);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(2400L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(2500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(1L);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(3400L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(3500L, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(2L);
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelayWithFlowableReorder() {
        int n = 3;

        PublishSubject<Integer> source = PublishSubject.create();
        final List<PublishSubject<Integer>> subjects = new ArrayList<PublishSubject<Integer>>();
        for (int i = 0; i < n; i++) {
            subjects.add(PublishSubject.<Integer> create());
        }

        Flowable<Integer> result = source.delay(new Function<Integer, Flowable<Integer>>() {

            @Override
            public Flowable<Integer> call(Integer t1) {
                return subjects.get(t1);
            }
        });

        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        result.subscribe(o);

        for (int i = 0; i < n; i++) {
            source.onNext(i);
        }
        source.onComplete();

        inOrder.verify(o, never()).onNext(anyInt());
        inOrder.verify(o, never()).onComplete();

        for (int i = n - 1; i >= 0; i--) {
            subjects.get(i).onComplete();
            inOrder.verify(o).onNext(i);
        }

        inOrder.verify(o).onComplete();

        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testDelayEmitsEverything() {
        Flowable<Integer> source = Flowable.range(1, 5);
        Flowable<Integer> delayed = source.delay(500L, TimeUnit.MILLISECONDS, scheduler);
        delayed = delayed.doOnEach(new Action1<Notification<? super Integer>>() {

            @Override
            public void call(Notification<? super Integer> t1) {
                System.out.println(t1);
            }

        });
        TestObserver<Integer> observer = new TestObserver<Integer>();
        delayed.subscribe(observer);
        // all will be delivered after 500ms since range does not delay between them
        scheduler.advanceTimeBy(500L, TimeUnit.MILLISECONDS);
        observer.assertReceivedOnNext(Arrays.asList(1, 2, 3, 4, 5));
    }

    @Test
    public void testBackpressureWithTimedDelay() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(1, RxRingBuffer.SIZE * 2)
                .delay(100, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.computation())
                .map(new Function<Integer, Integer>() {

                    int c = 0;

                    @Override
                    public Integer call(Integer t) {
                        if (c++ <= 0) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }
                        }
                        return t;
                    }

                }).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(RxRingBuffer.SIZE * 2, ts.getOnNextEvents().size());
    }
    
    @Test
    public void testBackpressureWithSubscriptionTimedDelay() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(1, RxRingBuffer.SIZE * 2)
                .delaySubscription(100, TimeUnit.MILLISECONDS)
                .delay(100, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.computation())
                .map(new Function<Integer, Integer>() {

                    int c = 0;

                    @Override
                    public Integer call(Integer t) {
                        if (c++ <= 0) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }
                        }
                        return t;
                    }

                }).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(RxRingBuffer.SIZE * 2, ts.getOnNextEvents().size());
    }

    @Test
    public void testBackpressureWithSelectorDelay() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(1, RxRingBuffer.SIZE * 2)
                .delay(new Function<Integer, Flowable<Long>>() {

                    @Override
                    public Flowable<Long> call(Integer i) {
                        return Flowable.timer(100, TimeUnit.MILLISECONDS);
                    }

                })
                .observeOn(Schedulers.computation())
                .map(new Function<Integer, Integer>() {

                    int c = 0;

                    @Override
                    public Integer call(Integer t) {
                        if (c++ <= 0) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }
                        }
                        return t;
                    }

                }).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(RxRingBuffer.SIZE * 2, ts.getOnNextEvents().size());
    }

    @Test
    public void testBackpressureWithSelectorDelayAndSubscriptionDelay() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(1, RxRingBuffer.SIZE * 2)
                .delay(new Supplier<Flowable<Long>>() {

                    @Override
                    public Flowable<Long> call() {
                        return Flowable.timer(500, TimeUnit.MILLISECONDS);
                    }
                }, new Function<Integer, Flowable<Long>>() {

                    @Override
                    public Flowable<Long> call(Integer i) {
                        return Flowable.timer(100, TimeUnit.MILLISECONDS);
                    }

                })
                .observeOn(Schedulers.computation())
                .map(new Function<Integer, Integer>() {

                    int c = 0;

                    @Override
                    public Integer call(Integer t) {
                        if (c++ <= 0) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                            }
                        }
                        return t;
                    }

                }).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(RxRingBuffer.SIZE * 2, ts.getOnNextEvents().size());
    }
}
