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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.Mockito;

import rx.Flowable;
import rx.Flowable.Operator;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Function;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class OperatorOnErrorResumeNextViaFunctionTest {

    @Test
    public void testResumeNextWithSynchronousExecution() {
        final AtomicReference<Throwable> receivedException = new AtomicReference<Throwable>();
        Flowable<String> w = Flowable.create(new Flowable.OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> observer) {
                observer.onNext("one");
                observer.onError(new Throwable("injected failure"));
                observer.onNext("two");
                observer.onNext("three");
            }
        });

        Function<Throwable, Flowable<String>> resume = new Function<Throwable, Flowable<String>>() {

            @Override
            public Flowable<String> call(Throwable t1) {
                receivedException.set(t1);
                return Flowable.just("twoResume", "threeResume");
            }

        };
        Flowable<String> observable = w.onErrorResumeNext(resume);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);

        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("one");
        verify(observer, Mockito.never()).onNext("two");
        verify(observer, Mockito.never()).onNext("three");
        verify(observer, times(1)).onNext("twoResume");
        verify(observer, times(1)).onNext("threeResume");
        assertNotNull(receivedException.get());
    }

    @Test
    public void testResumeNextWithAsyncExecution() {
        final AtomicReference<Throwable> receivedException = new AtomicReference<Throwable>();
        Subscription s = mock(Subscription.class);
        TestFlowable w = new TestFlowable(s, "one");
        Function<Throwable, Flowable<String>> resume = new Function<Throwable, Flowable<String>>() {

            @Override
            public Flowable<String> call(Throwable t1) {
                receivedException.set(t1);
                return Flowable.just("twoResume", "threeResume");
            }

        };
        Flowable<String> observable = Flowable.create(w).onErrorResumeNext(resume);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);

        try {
            w.t.join();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("one");
        verify(observer, Mockito.never()).onNext("two");
        verify(observer, Mockito.never()).onNext("three");
        verify(observer, times(1)).onNext("twoResume");
        verify(observer, times(1)).onNext("threeResume");
        assertNotNull(receivedException.get());
    }

    /**
     * Test that when a function throws an exception this is propagated through onError
     */
    @Test
    public void testFunctionThrowsError() {
        Subscription s = mock(Subscription.class);
        TestFlowable w = new TestFlowable(s, "one");
        Function<Throwable, Flowable<String>> resume = new Function<Throwable, Flowable<String>>() {

            @Override
            public Flowable<String> call(Throwable t1) {
                throw new RuntimeException("exception from function");
            }

        };
        Flowable<String> observable = Flowable.create(w).onErrorResumeNext(resume);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);

        try {
            w.t.join();
        } catch (InterruptedException e) {
            fail(e.getMessage());
        }

        // we should get the "one" value before the error
        verify(observer, times(1)).onNext("one");

        // we should have received an onError call on the Observer since the resume function threw an exception
        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, times(0)).onComplete();
    }

    /**
     * Test that we receive the onError if an exception is thrown from an operator that
     * does not have manual try/catch handling like map does.
     */
    @Test
    public void testOnErrorResumeReceivesErrorFromPreviousNonProtectedOperator() {
        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.just(1).lift(new Operator<String, Integer>() {

            @Override
            public Subscriber<? super Integer> call(Subscriber<? super String> t1) {
                throw new RuntimeException("failed");
            }

        }).onErrorResumeNext(new Function<Throwable, Flowable<String>>() {

            @Override
            public Flowable<String> call(Throwable t1) {
                if (t1.getMessage().equals("failed")) {
                    return Flowable.just("success");
                } else {
                    return Flowable.error(t1);
                }
            }

        }).subscribe(ts);

        ts.assertTerminalEvent();
        System.out.println(ts.getOnNextEvents());
        ts.assertValues(("success"));
    }

    /**
     * Test that we receive the onError if an exception is thrown from an operator that
     * does not have manual try/catch handling like map does.
     */
    @Test
    public void testOnErrorResumeReceivesErrorFromPreviousNonProtectedOperatorOnNext() {
        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.just(1).lift(new Operator<String, Integer>() {

            @Override
            public Subscriber<? super Integer> call(Subscriber<? super String> t1) {
                return new Subscriber<Integer>() {

                    @Override
                    public void onComplete() {
                        throw new RuntimeException("failed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        throw new RuntimeException("failed");
                    }

                    @Override
                    public void onNext(Integer t) {
                        throw new RuntimeException("failed");
                    }

                };
            }

        }).onErrorResumeNext(new Function<Throwable, Flowable<String>>() {

            @Override
            public Flowable<String> call(Throwable t1) {
                if (t1.getMessage().equals("failed")) {
                    return Flowable.just("success");
                } else {
                    return Flowable.error(t1);
                }
            }

        }).subscribe(ts);

        ts.assertTerminalEvent();
        System.out.println(ts.getOnNextEvents());
        ts.assertValues(("success"));
    }
    
    @Test
    public void testMapResumeAsyncNext() {
        // Trigger multiple failures
        Flowable<String> w = Flowable.just("one", "fail", "two", "three", "fail");

        // Introduce map function that fails intermittently (Map does not prevent this when the observer is a
        //  rx.operator incl onErrorResumeNextViaFlowable)
        w = w.map(new Function<String, String>() {
            @Override
            public String call(String s) {
                if ("fail".equals(s))
                    throw new RuntimeException("Forced Failure");
                System.out.println("BadMapper:" + s);
                return s;
            }
        });

        Flowable<String> observable = w.onErrorResumeNext(new Function<Throwable, Flowable<String>>() {

            @Override
            public Flowable<String> call(Throwable t1) {
                return Flowable.just("twoResume", "threeResume").subscribeOn(Schedulers.computation());
            }
            
        });

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        TestSubscriber<String> ts = new TestSubscriber<String>(observer);
        observable.subscribe(ts);
        ts.awaitTerminalEvent();

        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("one");
        verify(observer, Mockito.never()).onNext("two");
        verify(observer, Mockito.never()).onNext("three");
        verify(observer, times(1)).onNext("twoResume");
        verify(observer, times(1)).onNext("threeResume");
    }

    private static class TestFlowable implements Flowable.OnSubscribe<String> {

        final Subscription s;
        final String[] values;
        Thread t = null;

        public TestFlowable(Subscription s, String... values) {
            this.s = s;
            this.values = values;
        }

        @Override
        public void call(final Subscriber<? super String> observer) {
            System.out.println("TestFlowable subscribed to ...");
            observer.add(s);
            t = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        System.out.println("running TestFlowable thread");
                        for (String s : values) {
                            System.out.println("TestFlowable onNext: " + s);
                            observer.onNext(s);
                        }
                        throw new RuntimeException("Forced Failure");
                    } catch (Throwable e) {
                        observer.onError(e);
                    }
                }

            });
            System.out.println("starting TestFlowable thread");
            t.start();
            System.out.println("done starting TestFlowable thread");
        }

    }
    
    @Test
    public void testBackpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(0, 100000)
                .onErrorResumeNext(new Function<Throwable, Flowable<Integer>>() {

                    @Override
                    public Flowable<Integer> call(Throwable t1) {
                        return Flowable.just(1);
                    }

                })
                .observeOn(Schedulers.computation())
                .map(new Function<Integer, Integer>() {
                    int c = 0;

                    @Override
                    public Integer call(Integer t1) {
                        if (c++ <= 1) {
                            // slow
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        return t1;
                    }

                })
                .subscribe(ts);
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
    }
}
