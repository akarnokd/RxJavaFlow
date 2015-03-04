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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Matchers;

import rx.Notification;
import rx.Flowable;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.BiFunction;
import rx.functions.Func3;
import rx.functions.Func4;
import rx.functions.Func5;
import rx.functions.Func6;
import rx.functions.Func7;
import rx.functions.Func8;
import rx.functions.Func9;
import rx.functions.FuncN;
import rx.internal.util.RxRingBuffer;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class OnSubscribeCombineLatestTest {

    @Test
    public void testCombineLatestWithFunctionThatThrowsAnException() {
        @SuppressWarnings("unchecked")
        // mock calls don't do generics
        Observer<String> w = mock(Observer.class);

        PublishSubject<String> w1 = PublishSubject.create();
        PublishSubject<String> w2 = PublishSubject.create();

        Flowable<String> combined = Flowable.combineLatest(w1, w2, new BiFunction<String, String, String>() {
            @Override
            public String call(String v1, String v2) {
                throw new RuntimeException("I don't work.");
            }
        });
        combined.subscribe(w);

        w1.onNext("first value of w1");
        w2.onNext("first value of w2");

        verify(w, never()).onNext(anyString());
        verify(w, never()).onComplete();
        verify(w, times(1)).onError(Matchers.<RuntimeException> any());
    }

    @Test
    public void testCombineLatestDifferentLengthFlowableSequences1() {
        @SuppressWarnings("unchecked")
        // mock calls don't do generics
        Observer<String> w = mock(Observer.class);

        PublishSubject<String> w1 = PublishSubject.create();
        PublishSubject<String> w2 = PublishSubject.create();
        PublishSubject<String> w3 = PublishSubject.create();

        Flowable<String> combineLatestW = Flowable.combineLatest(w1, w2, w3, getConcat3StringsCombineLatestFunction());
        combineLatestW.subscribe(w);

        /* simulate sending data */
        // once for w1
        w1.onNext("1a");
        w2.onNext("2a");
        w3.onNext("3a");
        w1.onComplete();
        // twice for w2
        w2.onNext("2b");
        w2.onComplete();
        // 4 times for w3
        w3.onNext("3b");
        w3.onNext("3c");
        w3.onNext("3d");
        w3.onComplete();

        /* we should have been called 4 times on the Observer */
        InOrder inOrder = inOrder(w);
        inOrder.verify(w).onNext("1a2a3a");
        inOrder.verify(w).onNext("1a2b3a");
        inOrder.verify(w).onNext("1a2b3b");
        inOrder.verify(w).onNext("1a2b3c");
        inOrder.verify(w).onNext("1a2b3d");
        inOrder.verify(w, never()).onNext(anyString());
        inOrder.verify(w, times(1)).onComplete();
    }

    @Test
    public void testCombineLatestDifferentLengthFlowableSequences2() {
        @SuppressWarnings("unchecked")
        Observer<String> w = mock(Observer.class);

        PublishSubject<String> w1 = PublishSubject.create();
        PublishSubject<String> w2 = PublishSubject.create();
        PublishSubject<String> w3 = PublishSubject.create();

        Flowable<String> combineLatestW = Flowable.combineLatest(w1, w2, w3, getConcat3StringsCombineLatestFunction());
        combineLatestW.subscribe(w);

        /* simulate sending data */
        // 4 times for w1
        w1.onNext("1a");
        w1.onNext("1b");
        w1.onNext("1c");
        w1.onNext("1d");
        w1.onComplete();
        // twice for w2
        w2.onNext("2a");
        w2.onNext("2b");
        w2.onComplete();
        // 1 times for w3
        w3.onNext("3a");
        w3.onComplete();

        /* we should have been called 1 time only on the Observer since we only combine the "latest" we don't go back and loop through others once completed */
        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext("1d2b3a");
        inOrder.verify(w, never()).onNext(anyString());

        inOrder.verify(w, times(1)).onComplete();

    }

    @Test
    public void testCombineLatestWithInterleavingSequences() {
        @SuppressWarnings("unchecked")
        Observer<String> w = mock(Observer.class);

        PublishSubject<String> w1 = PublishSubject.create();
        PublishSubject<String> w2 = PublishSubject.create();
        PublishSubject<String> w3 = PublishSubject.create();

        Flowable<String> combineLatestW = Flowable.combineLatest(w1, w2, w3, getConcat3StringsCombineLatestFunction());
        combineLatestW.subscribe(w);

        /* simulate sending data */
        w1.onNext("1a");
        w2.onNext("2a");
        w2.onNext("2b");
        w3.onNext("3a");

        w1.onNext("1b");
        w2.onNext("2c");
        w2.onNext("2d");
        w3.onNext("3b");

        w1.onComplete();
        w2.onComplete();
        w3.onComplete();

        /* we should have been called 5 times on the Observer */
        InOrder inOrder = inOrder(w);
        inOrder.verify(w).onNext("1a2b3a");
        inOrder.verify(w).onNext("1b2b3a");
        inOrder.verify(w).onNext("1b2c3a");
        inOrder.verify(w).onNext("1b2d3a");
        inOrder.verify(w).onNext("1b2d3b");

        inOrder.verify(w, never()).onNext(anyString());
        inOrder.verify(w, times(1)).onComplete();
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testCombineLatest2Types() {
        BiFunction<String, Integer, String> combineLatestFunction = getConcatStringIntegerCombineLatestFunction();

        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable<String> w = Flowable.combineLatest(Flowable.just("one", "two"), Flowable.just(2, 3, 4), combineLatestFunction);
        w.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("two2");
        verify(observer, times(1)).onNext("two3");
        verify(observer, times(1)).onNext("two4");
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testCombineLatest3TypesA() {
        Func3<String, Integer, int[], String> combineLatestFunction = getConcatStringIntegerIntArrayCombineLatestFunction();

        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable<String> w = Flowable.combineLatest(Flowable.just("one", "two"), Flowable.just(2), Flowable.just(new int[] { 4, 5, 6 }), combineLatestFunction);
        w.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("two2[4, 5, 6]");
    }

    @SuppressWarnings("unchecked")
    /* mock calls don't do generics */
    @Test
    public void testCombineLatest3TypesB() {
        Func3<String, Integer, int[], String> combineLatestFunction = getConcatStringIntegerIntArrayCombineLatestFunction();

        /* define a Observer to receive aggregated events */
        Observer<String> observer = mock(Observer.class);

        Flowable<String> w = Flowable.combineLatest(Flowable.just("one"), Flowable.just(2), Flowable.just(new int[] { 4, 5, 6 }, new int[] { 7, 8 }), combineLatestFunction);
        w.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
        verify(observer, times(1)).onNext("one2[4, 5, 6]");
        verify(observer, times(1)).onNext("one2[7, 8]");
    }

    private Func3<String, String, String, String> getConcat3StringsCombineLatestFunction() {
        Func3<String, String, String, String> combineLatestFunction = new Func3<String, String, String, String>() {

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
        return combineLatestFunction;
    }

    private BiFunction<String, Integer, String> getConcatStringIntegerCombineLatestFunction() {
        BiFunction<String, Integer, String> combineLatestFunction = new BiFunction<String, Integer, String>() {

            @Override
            public String call(String s, Integer i) {
                return getStringValue(s) + getStringValue(i);
            }

        };
        return combineLatestFunction;
    }

    private Func3<String, Integer, int[], String> getConcatStringIntegerIntArrayCombineLatestFunction() {
        Func3<String, Integer, int[], String> combineLatestFunction = new Func3<String, Integer, int[], String>() {

            @Override
            public String call(String s, Integer i, int[] iArray) {
                return getStringValue(s) + getStringValue(i) + getStringValue(iArray);
            }

        };
        return combineLatestFunction;
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

    BiFunction<Integer, Integer, Integer> or = new BiFunction<Integer, Integer, Integer>() {
        @Override
        public Integer call(Integer t1, Integer t2) {
            return t1 | t2;
        }
    };

    @Test
    public void combineSimple() {
        PublishSubject<Integer> a = PublishSubject.create();
        PublishSubject<Integer> b = PublishSubject.create();

        Flowable<Integer> source = Flowable.combineLatest(a, b, or);

        @SuppressWarnings("unchecked")
        Observer<Object> observer = mock(Observer.class);
        InOrder inOrder = inOrder(observer);

        source.subscribe(observer);

        a.onNext(1);

        inOrder.verify(observer, never()).onNext(any());

        a.onNext(2);

        inOrder.verify(observer, never()).onNext(any());

        b.onNext(0x10);

        inOrder.verify(observer, times(1)).onNext(0x12);

        b.onNext(0x20);
        inOrder.verify(observer, times(1)).onNext(0x22);

        b.onComplete();

        inOrder.verify(observer, never()).onComplete();

        a.onComplete();

        inOrder.verify(observer, times(1)).onComplete();

        a.onNext(3);
        b.onNext(0x30);
        a.onComplete();
        b.onComplete();

        inOrder.verifyNoMoreInteractions();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void combineMultipleObservers() {
        PublishSubject<Integer> a = PublishSubject.create();
        PublishSubject<Integer> b = PublishSubject.create();

        Flowable<Integer> source = Flowable.combineLatest(a, b, or);

        @SuppressWarnings("unchecked")
        Observer<Object> observer1 = mock(Observer.class);
        @SuppressWarnings("unchecked")
        Observer<Object> observer2 = mock(Observer.class);

        InOrder inOrder1 = inOrder(observer1);
        InOrder inOrder2 = inOrder(observer2);

        source.subscribe(observer1);
        source.subscribe(observer2);

        a.onNext(1);

        inOrder1.verify(observer1, never()).onNext(any());
        inOrder2.verify(observer2, never()).onNext(any());

        a.onNext(2);

        inOrder1.verify(observer1, never()).onNext(any());
        inOrder2.verify(observer2, never()).onNext(any());

        b.onNext(0x10);

        inOrder1.verify(observer1, times(1)).onNext(0x12);
        inOrder2.verify(observer2, times(1)).onNext(0x12);

        b.onNext(0x20);
        inOrder1.verify(observer1, times(1)).onNext(0x22);
        inOrder2.verify(observer2, times(1)).onNext(0x22);

        b.onComplete();

        inOrder1.verify(observer1, never()).onComplete();
        inOrder2.verify(observer2, never()).onComplete();

        a.onComplete();

        inOrder1.verify(observer1, times(1)).onComplete();
        inOrder2.verify(observer2, times(1)).onComplete();

        a.onNext(3);
        b.onNext(0x30);
        a.onComplete();
        b.onComplete();

        inOrder1.verifyNoMoreInteractions();
        inOrder2.verifyNoMoreInteractions();
        verify(observer1, never()).onError(any(Throwable.class));
        verify(observer2, never()).onError(any(Throwable.class));
    }

    @Test
    public void testFirstNeverProduces() {
        PublishSubject<Integer> a = PublishSubject.create();
        PublishSubject<Integer> b = PublishSubject.create();

        Flowable<Integer> source = Flowable.combineLatest(a, b, or);

        @SuppressWarnings("unchecked")
        Observer<Object> observer = mock(Observer.class);
        InOrder inOrder = inOrder(observer);

        source.subscribe(observer);

        b.onNext(0x10);
        b.onNext(0x20);

        a.onComplete();

        inOrder.verify(observer, times(1)).onComplete();
        verify(observer, never()).onNext(any());
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSecondNeverProduces() {
        PublishSubject<Integer> a = PublishSubject.create();
        PublishSubject<Integer> b = PublishSubject.create();

        Flowable<Integer> source = Flowable.combineLatest(a, b, or);

        @SuppressWarnings("unchecked")
        Observer<Object> observer = mock(Observer.class);
        InOrder inOrder = inOrder(observer);

        source.subscribe(observer);

        a.onNext(0x1);
        a.onNext(0x2);

        b.onComplete();
        a.onComplete();

        inOrder.verify(observer, times(1)).onComplete();
        verify(observer, never()).onNext(any());
        verify(observer, never()).onError(any(Throwable.class));
    }

    public void test0Sources() {

    }

    @Test
    public void test1ToNSources() {
        int n = 30;
        FuncN<List<Object>> func = new FuncN<List<Object>>() {

            @Override
            public List<Object> call(Object... args) {
                return Arrays.asList(args);
            }
        };
        for (int i = 1; i <= n; i++) {
            System.out.println("test1ToNSources: " + i + " sources");
            List<Flowable<Integer>> sources = new ArrayList<Flowable<Integer>>();
            List<Object> values = new ArrayList<Object>();
            for (int j = 0; j < i; j++) {
                sources.add(Flowable.just(j));
                values.add(j);
            }

            Flowable<List<Object>> result = Flowable.combineLatest(sources, func);

            @SuppressWarnings("unchecked")
            Observer<List<Object>> o = mock(Observer.class);

            result.subscribe(o);

            verify(o).onNext(values);
            verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }

    @Test(timeout = 5000)
    public void test1ToNSourcesScheduled() throws InterruptedException {
        int n = 10;
        FuncN<List<Object>> func = new FuncN<List<Object>>() {

            @Override
            public List<Object> call(Object... args) {
                return Arrays.asList(args);
            }
        };
        for (int i = 1; i <= n; i++) {
            System.out.println("test1ToNSourcesScheduled: " + i + " sources");
            List<Flowable<Integer>> sources = new ArrayList<Flowable<Integer>>();
            List<Object> values = new ArrayList<Object>();
            for (int j = 0; j < i; j++) {
                sources.add(Flowable.just(j).subscribeOn(Schedulers.io()));
                values.add(j);
            }

            Flowable<List<Object>> result = Flowable.combineLatest(sources, func);

            @SuppressWarnings("unchecked")
            final Observer<List<Object>> o = mock(Observer.class);

            final CountDownLatch cdl = new CountDownLatch(1);

            Subscriber<List<Object>> s = new Subscriber<List<Object>>() {

                @Override
                public void onNext(List<Object> t) {
                    o.onNext(t);
                }

                @Override
                public void onError(Throwable e) {
                    o.onError(e);
                    cdl.countDown();
                }

                @Override
                public void onComplete() {
                    o.onComplete();
                    cdl.countDown();
                }
            };

            result.subscribe(s);

            cdl.await();

            verify(o).onNext(values);
            verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }

    @Test
    public void test2SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, new BiFunction<Integer, Integer, List<Integer>>() {
            @Override
            public List<Integer> call(Integer t1, Integer t2) {
                return Arrays.asList(t1, t2);
            }
        });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void test3SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);
        Flowable<Integer> s3 = Flowable.just(3);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, s3,
                new Func3<Integer, Integer, Integer, List<Integer>>() {
                    @Override
                    public List<Integer> call(Integer t1, Integer t2, Integer t3) {
                        return Arrays.asList(t1, t2, t3);
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2, 3));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void test4SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);
        Flowable<Integer> s3 = Flowable.just(3);
        Flowable<Integer> s4 = Flowable.just(4);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, s3, s4,
                new Func4<Integer, Integer, Integer, Integer, List<Integer>>() {
                    @Override
                    public List<Integer> call(Integer t1, Integer t2, Integer t3, Integer t4) {
                        return Arrays.asList(t1, t2, t3, t4);
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2, 3, 4));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void test5SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);
        Flowable<Integer> s3 = Flowable.just(3);
        Flowable<Integer> s4 = Flowable.just(4);
        Flowable<Integer> s5 = Flowable.just(5);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, s3, s4, s5,
                new Func5<Integer, Integer, Integer, Integer, Integer, List<Integer>>() {
                    @Override
                    public List<Integer> call(Integer t1, Integer t2, Integer t3, Integer t4, Integer t5) {
                        return Arrays.asList(t1, t2, t3, t4, t5);
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2, 3, 4, 5));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void test6SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);
        Flowable<Integer> s3 = Flowable.just(3);
        Flowable<Integer> s4 = Flowable.just(4);
        Flowable<Integer> s5 = Flowable.just(5);
        Flowable<Integer> s6 = Flowable.just(6);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, s3, s4, s5, s6,
                new Func6<Integer, Integer, Integer, Integer, Integer, Integer, List<Integer>>() {
                    @Override
                    public List<Integer> call(Integer t1, Integer t2, Integer t3, Integer t4, Integer t5, Integer t6) {
                        return Arrays.asList(t1, t2, t3, t4, t5, t6);
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2, 3, 4, 5, 6));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void test7SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);
        Flowable<Integer> s3 = Flowable.just(3);
        Flowable<Integer> s4 = Flowable.just(4);
        Flowable<Integer> s5 = Flowable.just(5);
        Flowable<Integer> s6 = Flowable.just(6);
        Flowable<Integer> s7 = Flowable.just(7);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, s3, s4, s5, s6, s7,
                new Func7<Integer, Integer, Integer, Integer, Integer, Integer, Integer, List<Integer>>() {
                    @Override
                    public List<Integer> call(Integer t1, Integer t2, Integer t3, Integer t4, Integer t5, Integer t6, Integer t7) {
                        return Arrays.asList(t1, t2, t3, t4, t5, t6, t7);
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2, 3, 4, 5, 6, 7));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void test8SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);
        Flowable<Integer> s3 = Flowable.just(3);
        Flowable<Integer> s4 = Flowable.just(4);
        Flowable<Integer> s5 = Flowable.just(5);
        Flowable<Integer> s6 = Flowable.just(6);
        Flowable<Integer> s7 = Flowable.just(7);
        Flowable<Integer> s8 = Flowable.just(8);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, s3, s4, s5, s6, s7, s8,
                new Func8<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, List<Integer>>() {
                    @Override
                    public List<Integer> call(Integer t1, Integer t2, Integer t3, Integer t4, Integer t5, Integer t6, Integer t7, Integer t8) {
                        return Arrays.asList(t1, t2, t3, t4, t5, t6, t7, t8);
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void test9SourcesOverload() {
        Flowable<Integer> s1 = Flowable.just(1);
        Flowable<Integer> s2 = Flowable.just(2);
        Flowable<Integer> s3 = Flowable.just(3);
        Flowable<Integer> s4 = Flowable.just(4);
        Flowable<Integer> s5 = Flowable.just(5);
        Flowable<Integer> s6 = Flowable.just(6);
        Flowable<Integer> s7 = Flowable.just(7);
        Flowable<Integer> s8 = Flowable.just(8);
        Flowable<Integer> s9 = Flowable.just(9);

        Flowable<List<Integer>> result = Flowable.combineLatest(s1, s2, s3, s4, s5, s6, s7, s8, s9,
                new Func9<Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, Integer, List<Integer>>() {
                    @Override
                    public List<Integer> call(Integer t1, Integer t2, Integer t3, Integer t4, Integer t5, Integer t6, Integer t7, Integer t8, Integer t9) {
                        return Arrays.asList(t1, t2, t3, t4, t5, t6, t7, t8, t9);
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onNext(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testZeroSources() {
        Flowable<Object> result = Flowable.combineLatest(Collections.<Flowable<Object>> emptyList(), new FuncN<Object>() {

            @Override
            public Object call(Object... args) {
                return args;
            }

        });

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        result.subscribe(o);

        verify(o).onComplete();
        verify(o, never()).onNext(any());
        verify(o, never()).onError(any(Throwable.class));

    }

    @Test
    public void testBackpressureLoop() {
        for (int i = 0; i < 5000; i++) {
            testBackpressure();
        }
    }
    
    @Test
    public void testBackpressure() {
        BiFunction<String, Integer, String> combineLatestFunction = getConcatStringIntegerCombineLatestFunction();

        int NUM = Flow.defaultBufferSize() * 4;
        TestSubscriber<String> ts = new TestSubscriber<String>();
        Flowable.combineLatest(Flowable.just("one", "two"),
                Flowable.range(2, NUM), combineLatestFunction).
                observeOn(Schedulers.computation()).subscribe(ts);

        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        List<String> events = ts.getOnNextEvents();
        assertEquals("two2", events.get(0));
        assertEquals("two3", events.get(1));
        assertEquals("two4", events.get(2));
        assertEquals(NUM, events.size());
    }

    @Test
    public void testWithCombineLatestIssue1717() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger count = new AtomicInteger();
        final int SIZE = 2000;
        Flowable<Long> timer = Flowable.timer(0, 1, TimeUnit.MILLISECONDS)
                .observeOn(Schedulers.newThread())
                .doOnEach(new Action1<Notification<? super Long>>() {

                    @Override
                    public void call(Notification<? super Long> n) {
                        //                        System.out.println(n);
                        if (count.incrementAndGet() >= SIZE) {
                            latch.countDown();
                        }
                    }

                }).take(SIZE);

        TestSubscriber<Long> ts = new TestSubscriber<Long>();

        Flowable.combineLatest(timer, Flowable.<Integer> never(), new BiFunction<Long, Integer, Long>() {

            @Override
            public Long call(Long t1, Integer t2) {
                return t1;
            }

        }).subscribe(ts);

        if (!latch.await(SIZE + 1000, TimeUnit.MILLISECONDS)) {
            fail("timed out");
        }

        assertEquals(SIZE, count.get());
    }
    
    @Test(timeout=10000)
    public void testCombineLatestRequestOverflow() throws InterruptedException {
        List<Flowable<Integer>> sources = Arrays.asList(Flowable.from(Arrays.asList(1,2,3,4)), Flowable.from(Arrays.asList(5,6,7,8)));
        Flowable<Integer> o = Flowable.combineLatest(sources,new FuncN<Integer>() {
            @Override
            public Integer call(Object... args) {
               return (Integer) args[0];
            }});
        //should get at least 4
        final CountDownLatch latch = new CountDownLatch(4);
        o.subscribeOn(Schedulers.computation()).subscribe(new Subscriber<Integer>() {
            
            @Override
            public void onStart() {
                request(2);
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
                request(Long.MAX_VALUE-1);
            }});
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

}
