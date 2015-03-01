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
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import org.junit.Test;
import org.mockito.*;

import rx.*;
import rx.Observable.OnSubscribe;
import rx.Observable;
import rx.Observer;
import rx.functions.*;
import rx.internal.util.RxRingBuffer;
import rx.observables.GroupedObservable;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;
import rx.subscriptions.Subscriptions;

public class OperatorRetryTest {

    @Test
    public void iterativeBackoff() {
        @SuppressWarnings("unchecked")
        Observer<String> consumer = mock(Observer.class);
        Observable<String> producer = Observable.create(new OnSubscribe<String>() {

            private AtomicInteger count = new AtomicInteger(4);
            long last = System.currentTimeMillis();

            @Override
            public void call(Subscriber<? super String> t1) {
                System.out.println(count.get() + " @ " + String.valueOf(last - System.currentTimeMillis()));
                last = System.currentTimeMillis();
                if (count.getAndDecrement() == 0) {
                    t1.onNext("hello");
                    t1.onCompleted();
                }
                else 
                    t1.onError(new RuntimeException());
            }
            
        });
        TestSubscriber<String> ts = new TestSubscriber<String>(consumer);
        producer.retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {

            @Override
            public Observable<?> call(Observable<? extends Throwable> attempts) {
                // Worker w = Schedulers.computation().createWorker();
                return attempts
                    .map(new Func1<Throwable, Tuple>() {
                        @Override
                        public Tuple call(Throwable n) {
                            return new Tuple(new Long(1), n);
                        }})
                    .scan(new Func2<Tuple, Tuple, Tuple>(){
                        @Override
                        public Tuple call(Tuple t, Tuple n) {
                            return new Tuple(t.count + n.count, n.n);
                        }})
                    .flatMap(new Func1<Tuple, Observable<Long>>() {
                        @Override
                        public Observable<Long> call(Tuple t) {
                            System.out.println("Retry # "+t.count);
                            return t.count > 20 ? 
                                Observable.<Long>error(t.n) :
                                Observable.timer(t.count *1L, TimeUnit.MILLISECONDS);
                    }});
            }
        }).subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();

        InOrder inOrder = inOrder(consumer);
        inOrder.verify(consumer, never()).onError(any(Throwable.class));
        inOrder.verify(consumer, times(1)).onNext("hello");
        inOrder.verify(consumer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();

    }

    public static class Tuple {
        Long count;
        Throwable n;

        Tuple(Long c, Throwable n) {
            count = c;
            this.n = n;
        }
    }

    @Test
    public void testRetryIndefinitely() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        int NUM_RETRIES = 20;
        Observable<String> origin = Observable.create(new FuncWithErrors(NUM_RETRIES));
        origin.retry().unsafeSubscribe(new TestSubscriber<String>(observer));

        InOrder inOrder = inOrder(observer);
        // should show 3 attempts
        inOrder.verify(observer, times(NUM_RETRIES + 1)).onNext("beginningEveryTime");
        // should have no errors
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        // should have a single success
        inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
        // should have a single successful onCompleted
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSchedulingNotificationHandler() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        int NUM_RETRIES = 2;
        Observable<String> origin = Observable.create(new FuncWithErrors(NUM_RETRIES));
        TestSubscriber<String> subscriber = new TestSubscriber<String>(observer);
        origin.retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
            @Override
            public Observable<?> call(Observable<? extends Throwable> t1) {
                return t1.observeOn(Schedulers.computation()).map(new Func1<Throwable, Void>() {
                    @Override
                    public Void call(Throwable t1) {
                        return null;
                    }
                }).startWith((Void) null);
            }
        }).subscribe(subscriber);

        subscriber.awaitTerminalEvent();
        InOrder inOrder = inOrder(observer);
        // should show 3 attempts
        inOrder.verify(observer, times(1 + NUM_RETRIES)).onNext("beginningEveryTime");
        // should have no errors
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        // should have a single success
        inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
        // should have a single successful onCompleted
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnNextFromNotificationHandler() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        int NUM_RETRIES = 2;
        Observable<String> origin = Observable.create(new FuncWithErrors(NUM_RETRIES));
        origin.retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
            @Override
            public Observable<?> call(Observable<? extends Throwable> t1) {
                return t1.map(new Func1<Throwable, Void>() {

                    @Override
                    public Void call(Throwable t1) {
                        return null;
                    }
                }).startWith((Void) null);
            }
        }).subscribe(observer);

        InOrder inOrder = inOrder(observer);
        // should show 3 attempts
        inOrder.verify(observer, times(NUM_RETRIES + 1)).onNext("beginningEveryTime");
        // should have no errors
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        // should have a single success
        inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
        // should have a single successful onCompleted
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnCompletedFromNotificationHandler() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Observable<String> origin = Observable.create(new FuncWithErrors(1));
        TestSubscriber<String> subscriber = new TestSubscriber<String>(observer);
        origin.retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
            @Override
            public Observable<?> call(Observable<? extends Throwable> t1) {
                return Observable.empty();
            }
        }).subscribe(subscriber);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, never()).onNext("beginningEveryTime");
        inOrder.verify(observer, never()).onNext("onSuccessOnly");
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verify(observer, never()).onError(any(Exception.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testOnErrorFromNotificationHandler() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Observable<String> origin = Observable.create(new FuncWithErrors(2));
        origin.retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
            @Override
            public Observable<?> call(Observable<? extends Throwable> t1) {
                return Observable.error(new RuntimeException());
            }
        }).subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, never()).onNext("beginningEveryTime");
        inOrder.verify(observer, never()).onNext("onSuccessOnly");
        inOrder.verify(observer, never()).onCompleted();
        inOrder.verify(observer, times(1)).onError(any(IllegalStateException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleSubscriptionOnFirst() throws Exception {
        final AtomicInteger inc = new AtomicInteger(0);
        Observable.OnSubscribe<Integer> onSubscribe = new OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                final int emit = inc.incrementAndGet();
                subscriber.onNext(emit);
                subscriber.onCompleted();
            }
        };

        int first = Observable.create(onSubscribe)
                .retryWhen(new Func1<Observable<? extends Throwable>, Observable<?>>() {
                    @Override
                    public Observable<?> call(Observable<? extends Throwable> attempt) {
                        return attempt.zipWith(Observable.just(1), new Func2<Throwable, Integer, Void>() {
                            @Override
                            public Void call(Throwable o, Integer integer) {
                                return null;
                            }
                        });
                    }
                })
                .toBlocking()
                .first();

        assertEquals("Observer did not receive the expected output", 1, first);
        assertEquals("Subscribe was not called once", 1, inc.get());
    }

    @Test
    public void testOriginFails() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Observable<String> origin = Observable.create(new FuncWithErrors(1));
        origin.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("beginningEveryTime");
        inOrder.verify(observer, times(1)).onError(any(RuntimeException.class));
        inOrder.verify(observer, never()).onNext("onSuccessOnly");
        inOrder.verify(observer, never()).onCompleted();
    }

    @Test
    public void testRetryFail() {
        int NUM_RETRIES = 1;
        int NUM_FAILURES = 2;
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Observable<String> origin = Observable.create(new FuncWithErrors(NUM_FAILURES));
        origin.retry(NUM_RETRIES).subscribe(observer);

        InOrder inOrder = inOrder(observer);
        // should show 2 attempts (first time fail, second time (1st retry) fail)
        inOrder.verify(observer, times(1 + NUM_RETRIES)).onNext("beginningEveryTime");
        // should only retry once, fail again and emit onError
        inOrder.verify(observer, times(1)).onError(any(RuntimeException.class));
        // no success
        inOrder.verify(observer, never()).onNext("onSuccessOnly");
        inOrder.verify(observer, never()).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testRetrySuccess() {
        int NUM_FAILURES = 1;
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Observable<String> origin = Observable.create(new FuncWithErrors(NUM_FAILURES));
        origin.retry(3).subscribe(observer);

        InOrder inOrder = inOrder(observer);
        // should show 3 attempts
        inOrder.verify(observer, times(1 + NUM_FAILURES)).onNext("beginningEveryTime");
        // should have no errors
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        // should have a single success
        inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
        // should have a single successful onCompleted
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testInfiniteRetry() {
        int NUM_FAILURES = 20;
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Observable<String> origin = Observable.create(new FuncWithErrors(NUM_FAILURES));
        origin.retry().subscribe(observer);

        InOrder inOrder = inOrder(observer);
        // should show 3 attempts
        inOrder.verify(observer, times(1 + NUM_FAILURES)).onNext("beginningEveryTime");
        // should have no errors
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        // should have a single success
        inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
        // should have a single successful onCompleted
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }

    /**
     * Checks in a simple and synchronous way that retry resubscribes
     * after error. This test fails against 0.16.1-0.17.4, hangs on 0.17.5 and
     * passes in 0.17.6 thanks to fix for issue #1027.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testRetrySubscribesAgainAfterError() {

        // record emitted values with this action
        Action1<Integer> record = mock(Action1.class);
        InOrder inOrder = inOrder(record);

        // always throw an exception with this action
        Action1<Integer> throwException = mock(Action1.class);
        doThrow(new RuntimeException()).when(throwException).call(Mockito.anyInt());

        // create a retrying observable based on a PublishSubject
        PublishSubject<Integer> subject = PublishSubject.create();
        subject
        // record item
        .doOnNext(record)
        // throw a RuntimeException
                .doOnNext(throwException)
                // retry on error
                .retry()
                // subscribe and ignore
                .subscribe();

        inOrder.verifyNoMoreInteractions();

        subject.onNext(1);
        inOrder.verify(record).call(1);

        subject.onNext(2);
        inOrder.verify(record).call(2);

        subject.onNext(3);
        inOrder.verify(record).call(3);

        inOrder.verifyNoMoreInteractions();
    }

    public static class FuncWithErrors implements Observable.OnSubscribe<String> {

        private final int numFailures;
        private final AtomicInteger count = new AtomicInteger(0);

        FuncWithErrors(int count) {
            this.numFailures = count;
        }

        @Override
        public void call(final Subscriber<? super String> o) {
            o.setProducer(new Producer() {
                final AtomicLong req = new AtomicLong();
                @Override
                public void request(long n) {
                    if (n == Long.MAX_VALUE) {
                        o.onNext("beginningEveryTime");
                        if (count.getAndIncrement() < numFailures) {
                            o.onError(new RuntimeException("forced failure: " + count.get()));
                        } else {
                            o.onNext("onSuccessOnly");
                            o.onCompleted();
                        }
                        return;
                    }
                    if (n > 0 && req.getAndAdd(n) == 0) {
                        int i = count.getAndIncrement();
                        if (i < numFailures) {
                            o.onNext("beginningEveryTime");
                            o.onError(new RuntimeException("forced failure: " + count.get()));
                            req.decrementAndGet();
                        } else {
                            do {
                                if (i == numFailures) {
                                    o.onNext("beginningEveryTime");
                                } else
                                if (i > numFailures) {
                                    o.onNext("onSuccessOnly");
                                    o.onCompleted();
                                    break;
                                }
                                i = count.getAndIncrement();
                            } while (req.decrementAndGet() > 0);
                        }
                    }
                }
            });
        }
    }

    @Test
    public void testUnsubscribeFromRetry() {
        PublishSubject<Integer> subject = PublishSubject.create();
        final AtomicInteger count = new AtomicInteger(0);
        Subscription sub = subject.retry().subscribe(new Action1<Integer>() {
            @Override
            public void call(Integer n) {
                count.incrementAndGet();
            }
        });
        subject.onNext(1);
        sub.unsubscribe();
        subject.onNext(2);
        assertEquals(1, count.get());
    }

    @Test
    public void testRetryAllowsSubscriptionAfterAllSubscriptionsUnsubscribed() throws InterruptedException {
        final AtomicInteger subsCount = new AtomicInteger(0);
        OnSubscribe<String> onSubscribe = new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> s) {
                subsCount.incrementAndGet();
                s.add(new Subscription() {
                    boolean unsubscribed = false;

                    @Override
                    public void unsubscribe() {
                        subsCount.decrementAndGet();
                        unsubscribed = true;
                    }

                    @Override
                    public boolean isUnsubscribed() {
                        return unsubscribed;
                    }
                });
            }
        };
        Observable<String> stream = Observable.create(onSubscribe);
        Observable<String> streamWithRetry = stream.retry();
        Subscription sub = streamWithRetry.subscribe();
        assertEquals(1, subsCount.get());
        sub.unsubscribe();
        assertEquals(0, subsCount.get());
        streamWithRetry.subscribe();
        assertEquals(1, subsCount.get());
    }

    @Test
    public void testSourceObservableCallsUnsubscribe() throws InterruptedException {
        final AtomicInteger subsCount = new AtomicInteger(0);

        final TestSubscriber<String> ts = new TestSubscriber<String>();

        OnSubscribe<String> onSubscribe = new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> s) {
                // if isUnsubscribed is true that means we have a bug such as
                // https://github.com/ReactiveX/RxJava/issues/1024
                if (!s.isUnsubscribed()) {
                    subsCount.incrementAndGet();
                    s.onError(new RuntimeException("failed"));
                    // it unsubscribes the child directly
                    // this simulates various error/completion scenarios that could occur
                    // or just a source that proactively triggers cleanup
                    s.unsubscribe();
                } else {
                    s.onError(new RuntimeException());
                }
            }
        };

        Observable.create(onSubscribe).retry(3).subscribe(ts);
        assertEquals(4, subsCount.get()); // 1 + 3 retries
    }

    @Test
    public void testSourceObservableRetry1() throws InterruptedException {
        final AtomicInteger subsCount = new AtomicInteger(0);

        final TestSubscriber<String> ts = new TestSubscriber<String>();

        OnSubscribe<String> onSubscribe = new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> s) {
                subsCount.incrementAndGet();
                s.onError(new RuntimeException("failed"));
            }
        };

        Observable.create(onSubscribe).retry(1).subscribe(ts);
        assertEquals(2, subsCount.get());
    }

    @Test
    public void testSourceObservableRetry0() throws InterruptedException {
        final AtomicInteger subsCount = new AtomicInteger(0);

        final TestSubscriber<String> ts = new TestSubscriber<String>();

        OnSubscribe<String> onSubscribe = new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> s) {
                subsCount.incrementAndGet();
                s.onError(new RuntimeException("failed"));
            }
        };

        Observable.create(onSubscribe).retry(0).subscribe(ts);
        assertEquals(1, subsCount.get());
    }

    static final class SlowObservable implements Observable.OnSubscribe<Long> {

        final AtomicInteger efforts = new AtomicInteger(0);
        final AtomicInteger active = new AtomicInteger(0), maxActive = new AtomicInteger(0);
        final AtomicInteger nextBeforeFailure;

        private final int emitDelay;

        public SlowObservable(int emitDelay, int countNext) {
            this.emitDelay = emitDelay;
            this.nextBeforeFailure = new AtomicInteger(countNext);
        }

        @Override
        public void call(final Subscriber<? super Long> subscriber) {
            final AtomicBoolean terminate = new AtomicBoolean(false);
            efforts.getAndIncrement();
            active.getAndIncrement();
            maxActive.set(Math.max(active.get(), maxActive.get()));
            final Thread thread = new Thread() {
                @Override
                public void run() {
                    long nr = 0;
                    try {
                        while (!terminate.get()) {
                            Thread.sleep(emitDelay);
                            if (nextBeforeFailure.getAndDecrement() > 0) {
                                subscriber.onNext(nr++);
                            } else {
                                subscriber.onError(new RuntimeException("expected-failed"));
                            }
                        }
                    } catch (InterruptedException t) {
                    }
                }
            };
            thread.start();
            subscriber.add(Subscriptions.create(new Action0() {
                @Override
                public void call() {
                    terminate.set(true);
                    active.decrementAndGet();
                }
            }));
        }
    }

    /** Observer for listener on seperate thread */
    static final class AsyncObserver<T> implements Observer<T> {

        protected CountDownLatch latch = new CountDownLatch(1);

        protected Observer<T> target;

        /** Wrap existing Observer */
        public AsyncObserver(Observer<T> target) {
            this.target = target;
        }

        /** Wait */
        public void await() {
            try {
                latch.await();
            } catch (InterruptedException e) {
                fail("Test interrupted");
            }
        }

        // Observer implementation

        @Override
        public void onCompleted() {
            target.onCompleted();
            latch.countDown();
        }

        @Override
        public void onError(Throwable t) {
            target.onError(t);
            latch.countDown();
        }

        @Override
        public void onNext(T v) {
            target.onNext(v);
        }
    }

    @Test(timeout = 10000)
    public void testUnsubscribeAfterError() {

        @SuppressWarnings("unchecked")
        Observer<Long> observer = mock(Observer.class);

        // Observable that always fails after 100ms
        SlowObservable so = new SlowObservable(100, 0);
        Observable<Long> o = Observable.create(so).retry(5);

        AsyncObserver<Long> async = new AsyncObserver<Long>(observer);

        o.subscribe(async);

        async.await();

        InOrder inOrder = inOrder(observer);
        // Should fail once
        inOrder.verify(observer, times(1)).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onCompleted();

        assertEquals("Start 6 threads, retry 5 then fail on 6", 6, so.efforts.get());
        assertEquals("Only 1 active subscription", 1, so.maxActive.get());
    }

    @Test(timeout = 10000)
    public void testTimeoutWithRetry() {

        @SuppressWarnings("unchecked")
        Observer<Long> observer = mock(Observer.class);

        // Observable that sends every 100ms (timeout fails instead)
        SlowObservable so = new SlowObservable(100, 10);
        Observable<Long> o = Observable.create(so).timeout(80, TimeUnit.MILLISECONDS).retry(5);

        AsyncObserver<Long> async = new AsyncObserver<Long>(observer);

        o.subscribe(async);

        async.await();

        InOrder inOrder = inOrder(observer);
        // Should fail once
        inOrder.verify(observer, times(1)).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onCompleted();

        assertEquals("Start 6 threads, retry 5 then fail on 6", 6, so.efforts.get());
    }
    
    @Test(timeout = 15000)
    public void testRetryWithBackpressure() throws InterruptedException {
        final int NUM_RETRIES = RxRingBuffer.SIZE * 2;
        for (int i = 0; i < 400; i++) {
            @SuppressWarnings("unchecked")
            Observer<String> observer = mock(Observer.class);
            Observable<String> origin = Observable.create(new FuncWithErrors(NUM_RETRIES));
            TestSubscriber<String> ts = new TestSubscriber<String>(observer);
            origin.retry().observeOn(Schedulers.computation()).unsafeSubscribe(ts);
            ts.awaitTerminalEvent(5, TimeUnit.SECONDS);
            
            InOrder inOrder = inOrder(observer);
            // should have no errors
            verify(observer, never()).onError(any(Throwable.class));
            // should show NUM_RETRIES attempts
            inOrder.verify(observer, times(NUM_RETRIES + 1)).onNext("beginningEveryTime");
            // should have a single success
            inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
            // should have a single successful onCompleted
            inOrder.verify(observer, times(1)).onCompleted();
            inOrder.verifyNoMoreInteractions();
        }
    }
    @Test(timeout = 15000)
    public void testRetryWithBackpressureParallel() throws InterruptedException {
        final int NUM_RETRIES = RxRingBuffer.SIZE * 2;
        int ncpu = Runtime.getRuntime().availableProcessors();
        ExecutorService exec = Executors.newFixedThreadPool(Math.max(ncpu / 2, 1));
        final AtomicInteger timeouts = new AtomicInteger();
        final Map<Integer, List<String>> data = new ConcurrentHashMap<Integer, List<String>>();
        final Map<Integer, List<Throwable>> exceptions = new ConcurrentHashMap<Integer, List<Throwable>>();
        final Map<Integer, Integer> completions = new ConcurrentHashMap<Integer, Integer>();
        
        int m = 2000;
        final CountDownLatch cdl = new CountDownLatch(m);
        for (int i = 0; i < m; i++) {
            final int j = i;
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    final AtomicInteger nexts = new AtomicInteger();
                    try {
                        Observable<String> origin = Observable.create(new FuncWithErrors(NUM_RETRIES));
                        TestSubscriber<String> ts = new TestSubscriber<String>();
                        origin.retry().observeOn(Schedulers.computation()).unsafeSubscribe(ts);
                        ts.awaitTerminalEvent(2, TimeUnit.SECONDS);
                        if (ts.getOnNextEvents().size() != NUM_RETRIES + 2) {
                            data.put(j, ts.getOnNextEvents());
                        }
                        if (ts.getOnErrorEvents().size() != 0) {
                            exceptions.put(j, ts.getOnErrorEvents());
                        }
                        if (ts.getOnCompletedEvents().size() != 1) {
                            completions.put(j, ts.getOnCompletedEvents().size());
                        }
                    } catch (Throwable t) {
                        timeouts.incrementAndGet();
                        System.out.println(j + " | " + cdl.getCount() + " !!! " + nexts.get());
                    }
                    cdl.countDown();
                }
            });
        }
        exec.shutdown();
        cdl.await();
        assertEquals(0, timeouts.get());
        if (data.size() > 0) {
            fail("Data content mismatch: " + data);
        }
        if (exceptions.size() > 0) {
            fail("Exceptions received: " + exceptions);
        }
        if (completions.size() > 0) {
            fail("Multiple completions received: " + completions);
        }
    }
    @Test(timeout = 3000)
    public void testIssue1900() throws InterruptedException {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        final int NUM_MSG = 1034;
        final AtomicInteger count = new AtomicInteger();

        Observable<String> origin = Observable.range(0, NUM_MSG)
                .map(new Func1<Integer, String>() {
                    @Override
                    public String call(Integer t1) {
                        return "msg: " + count.incrementAndGet();
                    }
                });
        
        origin.retry()
        .groupBy(new Func1<String, String>() {
            @Override
            public String call(String t1) {
                return t1;
            }
        })
        .flatMap(new Func1<GroupedObservable<String,String>, Observable<String>>() {
            @Override
            public Observable<String> call(GroupedObservable<String, String> t1) {
                return t1.take(1);
            }
        })
        .unsafeSubscribe(new TestSubscriber<String>(observer));
        
        InOrder inOrder = inOrder(observer);
        // should show 3 attempts
        inOrder.verify(observer, times(NUM_MSG)).onNext(any(java.lang.String.class));
        //        // should have no errors
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        // should have a single success
        //inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
        // should have a single successful onCompleted
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }
    @Test(timeout = 3000)
    public void testIssue1900SourceNotSupportingBackpressure() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        final int NUM_MSG = 1034;
        final AtomicInteger count = new AtomicInteger();

        Observable<String> origin = Observable.create(new Observable.OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> o) {
                for(int i=0; i<NUM_MSG; i++) {
                    o.onNext("msg:" + count.incrementAndGet());
                }   
                o.onCompleted();
            }
        });
        
        origin.retry()
        .groupBy(new Func1<String, String>() {
            @Override
            public String call(String t1) {
                return t1;
            }
        })
        .flatMap(new Func1<GroupedObservable<String,String>, Observable<String>>() {
            @Override
            public Observable<String> call(GroupedObservable<String, String> t1) {
                return t1.take(1);
            }
        })
        .unsafeSubscribe(new TestSubscriber<String>(observer));
        
        InOrder inOrder = inOrder(observer);
        // should show 3 attempts
        inOrder.verify(observer, times(NUM_MSG)).onNext(any(java.lang.String.class));
        //        // should have no errors
        inOrder.verify(observer, never()).onError(any(Throwable.class));
        // should have a single success
        //inOrder.verify(observer, times(1)).onNext("onSuccessOnly");
        // should have a single successful onCompleted
        inOrder.verify(observer, times(1)).onCompleted();
        inOrder.verifyNoMoreInteractions();
    }

}
