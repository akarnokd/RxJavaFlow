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

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.mockito.Mockito;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Function;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class OperatorOnErrorResumeNextViaFlowableTest {

    @Test
    public void testResumeNext() {
        Subscription s = mock(Subscription.class);
        // Trigger failure on second element
        TestFlowable f = new TestFlowable(s, "one", "fail", "two", "three");
        Flowable<String> w = Flowable.create(f);
        Flowable<String> resume = Flowable.just("twoResume", "threeResume");
        Flowable<String> observable = w.onErrorResumeNext(resume);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);

        try {
            f.t.join();
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
    }

    @Test
    public void testMapResumeAsyncNext() {
        Subscription sr = mock(Subscription.class);
        // Trigger multiple failures
        Flowable<String> w = Flowable.just("one", "fail", "two", "three", "fail");
        // Resume Flowable is async
        TestFlowable f = new TestFlowable(sr, "twoResume", "threeResume");
        Flowable<String> resume = Flowable.create(f);

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

        Flowable<String> observable = w.onErrorResumeNext(resume);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);

        try {
            f.t.join();
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
    }
    
    @Test
    public void testResumeNextWithFailedOnSubscribe() {
        Subscription s = mock(Subscription.class);
        Flowable<String> testFlowable = Flowable.create(new OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> t1) {
                throw new RuntimeException("force failure");
            }
            
        });
        Flowable<String> resume = Flowable.just("resume");
        Flowable<String> observable = testFlowable.onErrorResumeNext(resume);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observable.subscribe(observer);

        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("resume");
    }
    
    @Test
    public void testResumeNextWithFailedOnSubscribeAsync() {
        Subscription s = mock(Subscription.class);
        Flowable<String> testFlowable = Flowable.create(new OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> t1) {
                throw new RuntimeException("force failure");
            }
            
        });
        Flowable<String> resume = Flowable.just("resume");
        Flowable<String> observable = testFlowable.subscribeOn(Schedulers.io()).onErrorResumeNext(resume);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        TestSubscriber<String> ts = new TestSubscriber<String>(observer);
        observable.subscribe(ts);

        ts.awaitTerminalEvent();
        
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("resume");
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
                            if ("fail".equals(s))
                                throw new RuntimeException("Forced Failure");
                            System.out.println("TestFlowable onNext: " + s);
                            observer.onNext(s);
                        }
                        System.out.println("TestFlowable onComplete()");
                        observer.onComplete();
                    } catch (Throwable e) {
                        System.out.println("TestFlowable onError: " + e);
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
                .onErrorResumeNext(Flowable.just(1))
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
