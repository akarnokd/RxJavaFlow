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
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import rx.Notification;
import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.BiFunction;
import rx.functions.Func3;
import rx.functions.FuncN;
import rx.functions.Functions;
import rx.internal.util.RxRingBuffer;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class OperatorZipTest {
    BiFunction<String, String, String> concat2Strings;
    PublishSubject<String> s1;
    PublishSubject<String> s2;
    Flowable<String> zipped;

    Observer<String> observer;
    InOrder inOrder;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        concat2Strings = new BiFunction<String, String, String>() {
            @Override
            public String call(String t1, String t2) {
                return t1 + "-" + t2;
            }
        };

        s1 = PublishSubject.create();
        s2 = PublishSubject.create();
        zipped = Flowable.zip(s1, s2, concat2Strings);

        observer = mock(Observer.class);
        inOrder = inOrder(observer);

        zipped.subscribe(observer);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCollectionSizeDifferentThanFunction() {
        FuncN<String> zipr = Functions.fromFunc(getConcatStringIntegerIntArrayZipr());
        //Func3<String, Integer, int[], String>

        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        @SuppressWarnings("rawtypes")
        Collection ws = java.util.Collections.singleton(Flowable.just("one", "two"));
        Flowable<String> w = Flowable.zip(ws, zipr);
        w.subscribe(observer);

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onNext(any(String.class));
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testStartpingDifferentLengthFlowableSequences1() {
        Observer<String> w = mock(Observer.class);

        TestFlowable w1 = new TestFlowable();
        TestFlowable w2 = new TestFlowable();
        TestFlowable w3 = new TestFlowable();

        Flowable<String> zipW = Flowable.zip(Flowable.create(w1), Flowable.create(w2), Flowable.create(w3), getConcat3StringsZipr());
        zipW.subscribe(w);

        /* simulate sending data */
        // once for w1
        w1.observer.onNext("1a");
        w1.observer.onComplete();
        // twice for w2
        w2.observer.onNext("2a");
        w2.observer.onNext("2b");
        w2.observer.onComplete();
        // 4 times for w3
        w3.observer.onNext("3a");
        w3.observer.onNext("3b");
        w3.observer.onNext("3c");
        w3.observer.onNext("3d");
        w3.observer.onComplete();

        /* we should have been called 1 time on the Observer */
        InOrder io = inOrder(w);
        io.verify(w).onNext("1a2a3a");

        io.verify(w, times(1)).onComplete();
    }

    @Test
    public void testStartpingDifferentLengthFlowableSequences2() {
        @SuppressWarnings("unchecked")
        Observer<String> w = mock(Observer.class);

        TestFlowable w1 = new TestFlowable();
        TestFlowable w2 = new TestFlowable();
        TestFlowable w3 = new TestFlowable();

        Flowable<String> zipW = Flowable.zip(Flowable.create(w1), Flowable.create(w2), Flowable.create(w3), getConcat3StringsZipr());
        zipW.subscribe(w);

        /* simulate sending data */
        // 4 times for w1
        w1.observer.onNext("1a");
        w1.observer.onNext("1b");
        w1.observer.onNext("1c");
        w1.observer.onNext("1d");
        w1.observer.onComplete();
        // twice for w2
        w2.observer.onNext("2a");
        w2.observer.onNext("2b");
        w2.observer.onComplete();
        // 1 times for w3
        w3.observer.onNext("3a");
        w3.observer.onComplete();

        /* we should have been called 1 time on the Observer */
        InOrder io = inOrder(w);
        io.verify(w).onNext("1a2a3a");

        io.verify(w, times(1)).onComplete();

    }

    BiFunction<Object, Object, String> zipr2 = new BiFunction<Object, Object, String>() {

        @Override
        public String call(Object t1, Object t2) {
            return "" + t1 + t2;
        }

    };
    Func3<Object, Object, Object, String> zipr3 = new Func3<Object, Object, Object, String>() {

        @Override
        public String call(Object t1, Object t2, Object t3) {
            return "" + t1 + t2 + t3;
        }

    };

    /**
     * Testing internal private logic due to the complexity so I want to use TDD to test as a I build it rather than relying purely on the overall functionality expected by the public methods.
     */
    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregatorSimple() {
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<String> r2 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable.zip(r1, r2, zipr2).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("hello");
        r2.onNext("world");

        InOrder inOrder = inOrder(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        inOrder.verify(observer, times(1)).onNext("helloworld");

        r1.onNext("hello ");
        r2.onNext("again");

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        inOrder.verify(observer, times(1)).onNext("hello again");

        r1.onComplete();
        r2.onComplete();

        inOrder.verify(observer, never()).onNext(anyString());
        verify(observer, times(1)).onComplete();
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregatorDifferentSizedResultsWithOnComplete() {
        /* create the aggregator which will execute the zip function when all Flowables provide values */
        /* define a Observer to receive aggregated events */
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<String> r2 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);
        Flowable.zip(r1, r2, zipr2).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("hello");
        r2.onNext("world");
        r2.onComplete();

        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, never()).onError(any(Throwable.class));
        inOrder.verify(observer, times(1)).onNext("helloworld");
        inOrder.verify(observer, times(1)).onComplete();

        r1.onNext("hi");
        r1.onComplete();

        inOrder.verify(observer, never()).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onComplete();
        inOrder.verify(observer, never()).onNext(anyString());
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregateMultipleTypes() {
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<Integer> r2 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable.zip(r1, r2, zipr2).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("hello");
        r2.onNext(1);
        r2.onComplete();

        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, never()).onError(any(Throwable.class));
        inOrder.verify(observer, times(1)).onNext("hello1");
        inOrder.verify(observer, times(1)).onComplete();

        r1.onNext("hi");
        r1.onComplete();

        inOrder.verify(observer, never()).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onComplete();
        inOrder.verify(observer, never()).onNext(anyString());
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregate3Types() {
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<Integer> r2 = PublishSubject.create();
        PublishSubject<List<Integer>> r3 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable.zip(r1, r2, r3, zipr3).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("hello");
        r2.onNext(2);
        r3.onNext(Arrays.asList(5, 6, 7));

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, times(1)).onNext("hello2[5, 6, 7]");
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregatorsWithDifferentSizesAndTiming() {
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<String> r2 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable.zip(r1, r2, zipr2).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("one");
        r1.onNext("two");
        r1.onNext("three");
        r2.onNext("A");

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, times(1)).onNext("oneA");

        r1.onNext("four");
        r1.onComplete();
        r2.onNext("B");
        verify(observer, times(1)).onNext("twoB");
        r2.onNext("C");
        verify(observer, times(1)).onNext("threeC");
        r2.onNext("D");
        verify(observer, times(1)).onNext("fourD");
        r2.onNext("E");
        verify(observer, never()).onNext("E");
        r2.onComplete();

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregatorError() {
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<String> r2 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable.zip(r1, r2, zipr2).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("hello");
        r2.onNext("world");

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, times(1)).onNext("helloworld");

        r1.onError(new RuntimeException(""));
        r1.onNext("hello");
        r2.onNext("again");

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        // we don't want to be called again after an error
        verify(observer, times(0)).onNext("helloagain");
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregatorUnsubscribe() {
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<String> r2 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Subscription subscription = Flowable.zip(r1, r2, zipr2).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("hello");
        r2.onNext("world");

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, times(1)).onNext("helloworld");

        subscription.unsubscribe();
        r1.onNext("hello");
        r2.onNext("again");

        verify(observer, times(0)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        // we don't want to be called again after an error
        verify(observer, times(0)).onNext("helloagain");
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testAggregatorEarlyCompletion() {
        PublishSubject<String> r1 = PublishSubject.create();
        PublishSubject<String> r2 = PublishSubject.create();
        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable.zip(r1, r2, zipr2).subscribe(observer);

        /* simulate the Flowables pushing data into the aggregator */
        r1.onNext("one");
        r1.onNext("two");
        r1.onComplete();
        r2.onNext("A");

        InOrder inOrder = inOrder(observer);

        inOrder.verify(observer, never()).onError(any(Throwable.class));
        inOrder.verify(observer, never()).onComplete();
        inOrder.verify(observer, times(1)).onNext("oneA");

        r2.onComplete();

        inOrder.verify(observer, never()).onError(any(Throwable.class));
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verify(observer, never()).onNext(anyString());
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testStart2Types() {
        BiFunction<String, Integer, String> zipr = getConcatStringIntegerZipr();

        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable<String> w = Flowable.zip(Flowable.just("one", "two"), Flowable.just(2, 3, 4), zipr);
        w.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("one2");
        verify(observer, times(1)).onNext("two3");
        verify(observer, never()).onNext("4");
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testStart3Types() {
        Func3<String, Integer, int[], String> zipr = getConcatStringIntegerIntArrayZipr();

        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable<String> w = Flowable.zip(Flowable.just("one", "two"), Flowable.just(2), Flowable.just(new int[] { 4, 5, 6 }), zipr);
        w.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("one2[4, 5, 6]");
        verify(observer, never()).onNext("two");
    }

    @Test
    public void testOnNextExceptionInvokesOnError() {
        BiFunction<Integer, Integer, Integer> zipr = getDivideZipr();

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);

        Flowable<Integer> w = Flowable.zip(Flowable.just(10, 20, 30), Flowable.just(0, 1, 2), zipr);
        w.subscribe(observer);

        verify(observer, times(1)).onError(any(Throwable.class));
    }

    @Test
    public void testOnFirstCompletion() {
        PublishSubject<String> oA = PublishSubject.create();
        PublishSubject<String> oB = PublishSubject.create();

        @SuppressWarnings("unchecked")
        Observer<String> obs = mock(Observer.class);

        Flowable<String> o = Flowable.zip(oA, oB, getConcat2Strings());
        o.subscribe(obs);

        InOrder io = inOrder(obs);

        oA.onNext("a1");
        io.verify(obs, never()).onNext(anyString());
        oB.onNext("b1");
        io.verify(obs, times(1)).onNext("a1-b1");
        oB.onNext("b2");
        io.verify(obs, never()).onNext(anyString());
        oA.onNext("a2");
        io.verify(obs, times(1)).onNext("a2-b2");

        oA.onNext("a3");
        oA.onNext("a4");
        oA.onNext("a5");
        oA.onComplete();

        // SHOULD ONCOMPLETE BE EMITTED HERE INSTEAD OF WAITING
        // FOR B3, B4, B5 TO BE EMITTED?

        oB.onNext("b3");
        oB.onNext("b4");
        oB.onNext("b5");

        io.verify(obs, times(1)).onNext("a3-b3");
        io.verify(obs, times(1)).onNext("a4-b4");
        io.verify(obs, times(1)).onNext("a5-b5");

        // WE RECEIVE THE ONCOMPLETE HERE
        io.verify(obs, times(1)).onComplete();

        oB.onNext("b6");
        oB.onNext("b7");
        oB.onNext("b8");
        oB.onNext("b9");
        // never completes (infinite stream for example)

        // we should receive nothing else despite oB continuing after oA completed
        io.verifyNoMoreInteractions();
    }

    @Test
    public void testOnErrorTermination() {
        PublishSubject<String> oA = PublishSubject.create();
        PublishSubject<String> oB = PublishSubject.create();

        @SuppressWarnings("unchecked")
        Observer<String> obs = mock(Observer.class);

        Flowable<String> o = Flowable.zip(oA, oB, getConcat2Strings());
        o.subscribe(obs);

        InOrder io = inOrder(obs);

        oA.onNext("a1");
        io.verify(obs, never()).onNext(anyString());
        oB.onNext("b1");
        io.verify(obs, times(1)).onNext("a1-b1");
        oB.onNext("b2");
        io.verify(obs, never()).onNext(anyString());
        oA.onNext("a2");
        io.verify(obs, times(1)).onNext("a2-b2");

        oA.onNext("a3");
        oA.onNext("a4");
        oA.onNext("a5");
        oA.onError(new RuntimeException("forced failure"));

        // it should emit failure immediately
        io.verify(obs, times(1)).onError(any(RuntimeException.class));

        oB.onNext("b3");
        oB.onNext("b4");
        oB.onNext("b5");
        oB.onNext("b6");
        oB.onNext("b7");
        oB.onNext("b8");
        oB.onNext("b9");
        // never completes (infinite stream for example)

        // we should receive nothing else despite oB continuing after oA completed
        io.verifyNoMoreInteractions();
    }

    private BiFunction<String, String, String> getConcat2Strings() {
        return new BiFunction<String, String, String>() {

            @Override
            public String call(String t1, String t2) {
                return t1 + "-" + t2;
            }
        };
    }

    private BiFunction<Integer, Integer, Integer> getDivideZipr() {
        BiFunction<Integer, Integer, Integer> zipr = new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer i1, Integer i2) {
                return i1 / i2;
            }

        };
        return zipr;
    }

    private Func3<String, String, String, String> getConcat3StringsZipr() {
        Func3<String, String, String, String> zipr = new Func3<String, String, String, String>() {

            @Override
            public String call(String a1, String a2, String a3) {
                if (a1 == null) {
                    a1 = "";
                }
                if (a2 == null) {
                    a2 = "";
                }
                if (a3 == null) {
                    a3 = "";
                }
                return a1 + a2 + a3;
            }

        };
        return zipr;
    }

    private BiFunction<String, Integer, String> getConcatStringIntegerZipr() {
        BiFunction<String, Integer, String> zipr = new BiFunction<String, Integer, String>() {

            @Override
            public String call(String s, Integer i) {
                return getStringValue(s) + getStringValue(i);
            }

        };
        return zipr;
    }

    private Func3<String, Integer, int[], String> getConcatStringIntegerIntArrayZipr() {
        Func3<String, Integer, int[], String> zipr = new Func3<String, Integer, int[], String>() {

            @Override
            public String call(String s, Integer i, int[] iArray) {
                return getStringValue(s) + getStringValue(i) + getStringValue(iArray);
            }

        };
        return zipr;
    }

    private static String getStringValue(Object o) {
        if (o == null) {
            return "";
        } else {
            if (o instanceof int[]) {
                return Arrays.toString((int[]) o);
            } else {
                return String.valueOf(o);
            }
        }
    }

    private static class TestFlowable implements Flowable.OnSubscribe<String> {

        Observer<? super String> observer;

        @Override
        public void call(Subscriber<? super String> observer) {
            // just store the variable where it can be accessed so we can manually trigger it
            this.observer = observer;
        }

    }

    @Test
    public void testFirstCompletesThenSecondInfinite() {
        s1.onNext("a");
        s1.onNext("b");
        s1.onComplete();
        s2.onNext("1");
        inOrder.verify(observer, times(1)).onNext("a-1");
        s2.onNext("2");
        inOrder.verify(observer, times(1)).onNext("b-2");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSecondInfiniteThenFirstCompletes() {
        s2.onNext("1");
        s2.onNext("2");
        s1.onNext("a");
        inOrder.verify(observer, times(1)).onNext("a-1");
        s1.onNext("b");
        inOrder.verify(observer, times(1)).onNext("b-2");
        s1.onComplete();
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSecondCompletesThenFirstInfinite() {
        s2.onNext("1");
        s2.onNext("2");
        s2.onComplete();
        s1.onNext("a");
        inOrder.verify(observer, times(1)).onNext("a-1");
        s1.onNext("b");
        inOrder.verify(observer, times(1)).onNext("b-2");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testFirstInfiniteThenSecondCompletes() {
        s1.onNext("a");
        s1.onNext("b");
        s2.onNext("1");
        inOrder.verify(observer, times(1)).onNext("a-1");
        s2.onNext("2");
        inOrder.verify(observer, times(1)).onNext("b-2");
        s2.onComplete();
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testFirstFails() {
        s2.onNext("a");
        s1.onError(new RuntimeException("Forced failure"));

        inOrder.verify(observer, times(1)).onError(any(RuntimeException.class));

        s2.onNext("b");
        s1.onNext("1");
        s1.onNext("2");

        inOrder.verify(observer, never()).onComplete();
        inOrder.verify(observer, never()).onNext(any(String.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSecondFails() {
        s1.onNext("a");
        s1.onNext("b");
        s2.onError(new RuntimeException("Forced failure"));

        inOrder.verify(observer, times(1)).onError(any(RuntimeException.class));

        s2.onNext("1");
        s2.onNext("2");

        inOrder.verify(observer, never()).onComplete();
        inOrder.verify(observer, never()).onNext(any(String.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testStartWithonComplete()Twice() {
        // issue: https://groups.google.com/forum/#!topic/rxjava/79cWTv3TFp0
        // The problem is the original "zip" implementation does not wrap
        // an internal observer with a SafeObserver. However, in the "zip",
        // it may calls "onComplete()" twice. That breaks the Rx contract.

        // This test tries to emulate this case.
        // As "mock(Observer.class)" will create an instance in the package "rx",
        // we need to wrap "mock(Observer.class)" with an observer instance
        // which is in the package "rx.operators".
        @SuppressWarnings("unchecked")
        final Observer<Integer> observer = mock(Observer.class);

        Flowable.zip(Flowable.just(1),
                Flowable.just(1), new BiFunction<Integer, Integer, Integer>() {
                    @Override
                    public Integer call(Integer a, Integer b) {
                        return a + b;
                    }
                }).subscribe(new Observer<Integer>() {

            @Override
            public void onComplete() {
                observer.onComplete();
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onNext(Integer args) {
                observer.onNext(args);
            }

        });

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(2);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testStart() {
        Flowable<String> os = OBSERVABLE_OF_5_INTEGERS
                .zipWith(OBSERVABLE_OF_5_INTEGERS, new BiFunction<Integer, Integer, String>() {

                    @Override
                    public String call(Integer a, Integer b) {
                        return a + "-" + b;
                    }
                });

        final ArrayList<String> list = new ArrayList<String>();
        os.subscribe(new Action1<String>() {

            @Override
            public void call(String s) {
                System.out.println(s);
                list.add(s);
            }
        });

        assertEquals(5, list.size());
        assertEquals("1-1", list.get(0));
        assertEquals("2-2", list.get(1));
        assertEquals("5-5", list.get(4));
    }

    @Test
    public void testStartAsync() throws InterruptedException {
        Flowable<String> os = ASYNC_OBSERVABLE_OF_INFINITE_INTEGERS(new CountDownLatch(1)).onBackpressureBuffer()
                .zipWith(ASYNC_OBSERVABLE_OF_INFINITE_INTEGERS(new CountDownLatch(1)).onBackpressureBuffer(), new BiFunction<Integer, Integer, String>() {

                    @Override
                    public String call(Integer a, Integer b) {
                        return a + "-" + b;
                    }
                }).take(5);

        TestSubscriber<String> ts = new TestSubscriber<String>();
        os.subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();

        assertEquals(5, ts.getOnNextEvents().size());
        assertEquals("1-1", ts.getOnNextEvents().get(0));
        assertEquals("2-2", ts.getOnNextEvents().get(1));
        assertEquals("5-5", ts.getOnNextEvents().get(4));
    }

    @Test
    public void testStartInfiniteAndFinite() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final CountDownLatch infiniteFlowable = new CountDownLatch(1);
        Flowable<String> os = OBSERVABLE_OF_5_INTEGERS
                .zipWith(ASYNC_OBSERVABLE_OF_INFINITE_INTEGERS(infiniteFlowable), new BiFunction<Integer, Integer, String>() {

                    @Override
                    public String call(Integer a, Integer b) {
                        return a + "-" + b;
                    }
                });

        final ArrayList<String> list = new ArrayList<String>();
        os.subscribe(new Observer<String>() {

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
                latch.countDown();
            }

            @Override
            public void onNext(String s) {
                System.out.println(s);
                list.add(s);
            }
        });

        latch.await(1000, TimeUnit.MILLISECONDS);
        if (!infiniteFlowable.await(2000, TimeUnit.MILLISECONDS)) {
            throw new RuntimeException("didn't unsubscribe");
        }

        assertEquals(5, list.size());
        assertEquals("1-1", list.get(0));
        assertEquals("2-2", list.get(1));
        assertEquals("5-5", list.get(4));
    }

    @Test
    public void testEmitNull() {
        Flowable<Integer> oi = Flowable.just(1, null, 3);
        Flowable<String> os = Flowable.just("a", "b", null);
        Flowable<String> o = Flowable.zip(oi, os, new BiFunction<Integer, String, String>() {

            @Override
            public String call(Integer t1, String t2) {
                return t1 + "-" + t2;
            }

        });

        final ArrayList<String> list = new ArrayList<String>();
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String s) {
                System.out.println(s);
                list.add(s);
            }
        });

        assertEquals(3, list.size());
        assertEquals("1-a", list.get(0));
        assertEquals("null-b", list.get(1));
        assertEquals("3-null", list.get(2));
    }

    @Test
    public void testEmitMaterializedNotifications() {
        Flowable<Notification<Integer>> oi = Flowable.just(1, 2, 3).materialize();
        Flowable<Notification<String>> os = Flowable.just("a", "b", "c").materialize();
        Flowable<String> o = Flowable.zip(oi, os, new BiFunction<Notification<Integer>, Notification<String>, String>() {

            @Override
            public String call(Notification<Integer> t1, Notification<String> t2) {
                return t1.getKind() + "_" + t1.getValue() + "-" + t2.getKind() + "_" + t2.getValue();
            }

        });

        final ArrayList<String> list = new ArrayList<String>();
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String s) {
                System.out.println(s);
                list.add(s);
            }
        });

        assertEquals(4, list.size());
        assertEquals("OnNext_1-OnNext_a", list.get(0));
        assertEquals("OnNext_2-OnNext_b", list.get(1));
        assertEquals("OnNext_3-OnNext_c", list.get(2));
        assertEquals("onComplete()_null-onComplete()_null", list.get(3));
    }

    @Test
    public void testStartEmptyFlowables() {

        Flowable<String> o = Flowable.zip(Flowable.<Integer> empty(), Flowable.<String> empty(), new BiFunction<Integer, String, String>() {

            @Override
            public String call(Integer t1, String t2) {
                return t1 + "-" + t2;
            }

        });

        final ArrayList<String> list = new ArrayList<String>();
        o.subscribe(new Action1<String>() {

            @Override
            public void call(String s) {
                System.out.println(s);
                list.add(s);
            }
        });

        assertEquals(0, list.size());
    }

    @Test
    public void testStartEmptyList() {

        final Object invoked = new Object();
        Collection<Flowable<Object>> observables = Collections.emptyList();

        Flowable<Object> o = Flowable.zip(observables, new FuncN<Object>() {
            @Override
            public Object call(final Object... args) {
                assertEquals("No argument should have been passed", 0, args.length);
                return invoked;
            }
        });

        TestSubscriber<Object> ts = new TestSubscriber<Object>();
        o.subscribe(ts);
        ts.awaitTerminalEvent(200, TimeUnit.MILLISECONDS);
        ts.assertReceivedOnNext(Collections.emptyList());
    }

    /**
     * Expect NoSuchElementException instead of blocking forever as zip should emit onComplete() and no onNext
     * and last() expects at least a single response.
     */
    @Test(expected = NoSuchElementException.class)
    public void testStartEmptyListBlocking() {

        final Object invoked = new Object();
        Collection<Flowable<Object>> observables = Collections.emptyList();

        Flowable<Object> o = Flowable.zip(observables, new FuncN<Object>() {
            @Override
            public Object call(final Object... args) {
                assertEquals("No argument should have been passed", 0, args.length);
                return invoked;
            }
        });

        o.toBlocking().last();
    }

    @Test
    public void testBackpressureSync() {
        AtomicInteger generatedA = new AtomicInteger();
        AtomicInteger generatedB = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generatedA);
        Flowable<Integer> o2 = createInfiniteFlowable(generatedB);

        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.zip(o1, o2, new BiFunction<Integer, Integer, String>() {

            @Override
            public String call(Integer t1, Integer t2) {
                return t1 + "-" + t2;
            }

        }).take(Flow.defaultBufferSize() * 2).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(Flow.defaultBufferSize() * 2, ts.getOnNextEvents().size());
        assertTrue(generatedA.get() < (Flow.defaultBufferSize() * 3));
        assertTrue(generatedB.get() < (Flow.defaultBufferSize() * 3));
    }

    @Test
    public void testBackpressureAsync() {
        AtomicInteger generatedA = new AtomicInteger();
        AtomicInteger generatedB = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generatedA).subscribeOn(Schedulers.computation());
        Flowable<Integer> o2 = createInfiniteFlowable(generatedB).subscribeOn(Schedulers.computation());

        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.zip(o1, o2, new BiFunction<Integer, Integer, String>() {

            @Override
            public String call(Integer t1, Integer t2) {
                return t1 + "-" + t2;
            }

        }).take(Flow.defaultBufferSize() * 2).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(Flow.defaultBufferSize() * 2, ts.getOnNextEvents().size());
        assertTrue(generatedA.get() < (Flow.defaultBufferSize() * 3));
        assertTrue(generatedB.get() < (Flow.defaultBufferSize() * 3));
    }

    @Test
    public void testDownstreamBackpressureRequestsWithFiniteSyncFlowables() {
        AtomicInteger generatedA = new AtomicInteger();
        AtomicInteger generatedB = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generatedA).take(Flow.defaultBufferSize() * 2);
        Flowable<Integer> o2 = createInfiniteFlowable(generatedB).take(Flow.defaultBufferSize() * 2);

        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.zip(o1, o2, new BiFunction<Integer, Integer, String>() {

            @Override
            public String call(Integer t1, Integer t2) {
                return t1 + "-" + t2;
            }

        }).observeOn(Schedulers.computation()).take(Flow.defaultBufferSize() * 2).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(Flow.defaultBufferSize() * 2, ts.getOnNextEvents().size());
        System.out.println("Generated => A: " + generatedA.get() + " B: " + generatedB.get());
        assertTrue(generatedA.get() < (Flow.defaultBufferSize() * 3));
        assertTrue(generatedB.get() < (Flow.defaultBufferSize() * 3));
    }

    @Test
    public void testDownstreamBackpressureRequestsWithInfiniteAsyncFlowables() {
        AtomicInteger generatedA = new AtomicInteger();
        AtomicInteger generatedB = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generatedA).subscribeOn(Schedulers.computation());
        Flowable<Integer> o2 = createInfiniteFlowable(generatedB).subscribeOn(Schedulers.computation());

        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.zip(o1, o2, new BiFunction<Integer, Integer, String>() {

            @Override
            public String call(Integer t1, Integer t2) {
                return t1 + "-" + t2;
            }

        }).observeOn(Schedulers.computation()).take(Flow.defaultBufferSize() * 2).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(Flow.defaultBufferSize() * 2, ts.getOnNextEvents().size());
        System.out.println("Generated => A: " + generatedA.get() + " B: " + generatedB.get());
        assertTrue(generatedA.get() < (Flow.defaultBufferSize() * 4));
        assertTrue(generatedB.get() < (Flow.defaultBufferSize() * 4));
    }

    @Test
    public void testDownstreamBackpressureRequestsWithInfiniteSyncFlowables() {
        AtomicInteger generatedA = new AtomicInteger();
        AtomicInteger generatedB = new AtomicInteger();
        Flowable<Integer> o1 = createInfiniteFlowable(generatedA);
        Flowable<Integer> o2 = createInfiniteFlowable(generatedB);

        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.zip(o1, o2, new BiFunction<Integer, Integer, String>() {

            @Override
            public String call(Integer t1, Integer t2) {
                return t1 + "-" + t2;
            }

        }).observeOn(Schedulers.computation()).take(Flow.defaultBufferSize() * 2).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        assertEquals(Flow.defaultBufferSize() * 2, ts.getOnNextEvents().size());
        System.out.println("Generated => A: " + generatedA.get() + " B: " + generatedB.get());
        assertTrue(generatedA.get() < (Flow.defaultBufferSize() * 4));
        assertTrue(generatedB.get() < (Flow.defaultBufferSize() * 4));
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

    Flowable<Integer> OBSERVABLE_OF_5_INTEGERS = OBSERVABLE_OF_5_INTEGERS(new AtomicInteger());

    Flowable<Integer> OBSERVABLE_OF_5_INTEGERS(final AtomicInteger numEmitted) {
        return Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(final Subscriber<? super Integer> o) {
                for (int i = 1; i <= 5; i++) {
                    if (o.isUnsubscribed()) {
                        break;
                    }
                    numEmitted.incrementAndGet();
                    o.onNext(i);
                    Thread.yield();
                }
                o.onComplete();
            }

        });
    }

    Flowable<Integer> ASYNC_OBSERVABLE_OF_INFINITE_INTEGERS(final CountDownLatch latch) {
        return Flowable.create(new OnSubscribe<Integer>() {

            @Override
            public void call(final Subscriber<? super Integer> o) {
                Thread t = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        System.out.println("-------> subscribe to infinite sequence");
                        System.out.println("Starting thread: " + Thread.currentThread());
                        int i = 1;
                        while (!o.isUnsubscribed()) {
                            o.onNext(i++);
                            Thread.yield();
                        }
                        o.onComplete();
                        latch.countDown();
                        System.out.println("Ending thread: " + Thread.currentThread());
                    }
                });
                t.start();

            }

        });
    }

    @Test(timeout = 30000)
    public void testIssue1812() {
        // https://github.com/ReactiveX/RxJava/issues/1812
        Flowable<Integer> zip1 = Flowable.zip(Flowable.range(0, 1026), Flowable.range(0, 1026),
                new BiFunction<Integer, Integer, Integer>() {

                    @Override
                    public Integer call(Integer i1, Integer i2) {
                        return i1 + i2;
                    }
                });
        Flowable<Integer> zip2 = Flowable.zip(zip1, Flowable.range(0, 1026),
                new BiFunction<Integer, Integer, Integer>() {

                    @Override
                    public Integer call(Integer i1, Integer i2) {
                        return i1 + i2;
                    }
                });
        List<Integer> expected = new ArrayList<Integer>();
        for (int i = 0; i < 1026; i++) {
            expected.add(i * 3);
        }
        assertEquals(expected, zip2.toList().toBlocking().single());
    }
    @Test
    public void testUnboundedDownstreamOverrequesting() {
        Flowable<Integer> source = Flowable.range(1, 2).zipWith(Flowable.range(1, 2), new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + 10 * t2;
            }
        });
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>() {
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                requestMore(5);
            }
        };
        
        source.subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertTerminalEvent();
        ts.assertValues((11, 22));
    }
    @Test(timeout = 10000)
    public void testZipRace() {
        Flowable<Integer> src = Flowable.just(1).subscribeOn(Schedulers.computation());
        for (int i = 0; i < 100000; i++) {
            int value = Flowable.zip(src, src, new BiFunction<Integer, Integer, Integer>() {
                @Override
                public Integer call(Integer t1, Integer t2) {
                    return t1 + t2 * 10;
                }
            }).toBlocking().singleOrDefault(0);
            
            Assert.assertEquals(11, value);
        }
    }
    /** 
     * Request only a single value and don't wait for another request just
     * to emit an onComplete().
     */
    @Test
    public void testZipRequest1() {
        Flowable<Integer> src = Flowable.just(1).subscribeOn(Schedulers.computation());
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                requestMore(1);
            }
        };
        
        Flowable.zip(src, src, new BiFunction<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2 * 10;
            }
        }).subscribe(ts);
        
        ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
        ts.assertNoErrors();
        ts.assertValues((11));
    }
}
