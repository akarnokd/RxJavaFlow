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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.*;

import rx.Observable;
import rx.Observer;
import rx.exceptions.TestException;
import rx.functions.*;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class OperatorFlatMapTest {
    @Test
    public void testNormal() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        final List<Integer> list = Arrays.asList(1, 2, 3);

        Func1<Integer, List<Integer>> func = new Func1<Integer, List<Integer>>() {
            @Override
            public List<Integer> call(Integer t1) {
                return list;
            }
        };
        Func2<Integer, Integer, Integer> resFunc = new Func2<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 | t2;
            }
        };

        List<Integer> source = Arrays.asList(16, 32, 64);

        Observable.from(source).flatMapIterable(func, resFunc).subscribe(o);

        for (Integer s : source) {
            for (Integer v : list) {
                verify(o).onNext(s | v);
            }
        }
        verify(o).onCompleted();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testCollectionFunctionThrows() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        Func1<Integer, List<Integer>> func = new Func1<Integer, List<Integer>>() {
            @Override
            public List<Integer> call(Integer t1) {
                throw new TestException();
            }
        };
        Func2<Integer, Integer, Integer> resFunc = new Func2<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 | t2;
            }
        };

        List<Integer> source = Arrays.asList(16, 32, 64);

        Observable.from(source).flatMapIterable(func, resFunc).subscribe(o);

        verify(o, never()).onCompleted();
        verify(o, never()).onNext(any());
        verify(o).onError(any(TestException.class));
    }

    @Test
    public void testResultFunctionThrows() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        final List<Integer> list = Arrays.asList(1, 2, 3);

        Func1<Integer, List<Integer>> func = new Func1<Integer, List<Integer>>() {
            @Override
            public List<Integer> call(Integer t1) {
                return list;
            }
        };
        Func2<Integer, Integer, Integer> resFunc = new Func2<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                throw new TestException();
            }
        };

        List<Integer> source = Arrays.asList(16, 32, 64);

        Observable.from(source).flatMapIterable(func, resFunc).subscribe(o);

        verify(o, never()).onCompleted();
        verify(o, never()).onNext(any());
        verify(o).onError(any(TestException.class));
    }

    @Test
    public void testMergeError() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        Func1<Integer, Observable<Integer>> func = new Func1<Integer, Observable<Integer>>() {
            @Override
            public Observable<Integer> call(Integer t1) {
                return Observable.error(new TestException());
            }
        };
        Func2<Integer, Integer, Integer> resFunc = new Func2<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 | t2;
            }
        };

        List<Integer> source = Arrays.asList(16, 32, 64);

        Observable.from(source).flatMap(func, resFunc).subscribe(o);

        verify(o, never()).onCompleted();
        verify(o, never()).onNext(any());
        verify(o).onError(any(TestException.class));
    }

    <T, R> Func1<T, R> just(final R value) {
        return new Func1<T, R>() {

            @Override
            public R call(T t1) {
                return value;
            }
        };
    }

    <R> Func0<R> just0(final R value) {
        return new Func0<R>() {

            @Override
            public R call() {
                return value;
            }
        };
    }

    @Test
    public void testFlatMapTransformsNormal() {
        Observable<Integer> onNext = Observable.from(Arrays.asList(1, 2, 3));
        Observable<Integer> onCompleted = Observable.from(Arrays.asList(4));
        Observable<Integer> onError = Observable.from(Arrays.asList(5));

        Observable<Integer> source = Observable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        source.flatMap(just(onNext), just(onError), just0(onCompleted)).subscribe(o);

        verify(o, times(3)).onNext(1);
        verify(o, times(3)).onNext(2);
        verify(o, times(3)).onNext(3);
        verify(o).onNext(4);
        verify(o).onCompleted();

        verify(o, never()).onNext(5);
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testFlatMapTransformsException() {
        Observable<Integer> onNext = Observable.from(Arrays.asList(1, 2, 3));
        Observable<Integer> onCompleted = Observable.from(Arrays.asList(4));
        Observable<Integer> onError = Observable.from(Arrays.asList(5));

        Observable<Integer> source = Observable.concat(
                Observable.from(Arrays.asList(10, 20, 30)),
                Observable.<Integer> error(new RuntimeException("Forced failure!"))
                );
        

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        source.flatMap(just(onNext), just(onError), just0(onCompleted)).subscribe(o);

        verify(o, times(3)).onNext(1);
        verify(o, times(3)).onNext(2);
        verify(o, times(3)).onNext(3);
        verify(o).onNext(5);
        verify(o).onCompleted();
        verify(o, never()).onNext(4);

        verify(o, never()).onError(any(Throwable.class));
    }

    <R> Func0<R> funcThrow0(R r) {
        return new Func0<R>() {
            @Override
            public R call() {
                throw new TestException();
            }
        };
    }

    <T, R> Func1<T, R> funcThrow(T t, R r) {
        return new Func1<T, R>() {
            @Override
            public R call(T t) {
                throw new TestException();
            }
        };
    }

    @Test
    public void testFlatMapTransformsOnNextFuncThrows() {
        Observable<Integer> onCompleted = Observable.from(Arrays.asList(4));
        Observable<Integer> onError = Observable.from(Arrays.asList(5));

        Observable<Integer> source = Observable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        source.flatMap(funcThrow(1, onError), just(onError), just0(onCompleted)).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onCompleted();
    }

    @Test
    public void testFlatMapTransformsOnErrorFuncThrows() {
        Observable<Integer> onNext = Observable.from(Arrays.asList(1, 2, 3));
        Observable<Integer> onCompleted = Observable.from(Arrays.asList(4));
        Observable<Integer> onError = Observable.from(Arrays.asList(5));

        Observable<Integer> source = Observable.error(new TestException());

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        source.flatMap(just(onNext), funcThrow((Throwable) null, onError), just0(onCompleted)).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onCompleted();
    }

    @Test
    public void testFlatMapTransformsOnCompletedFuncThrows() {
        Observable<Integer> onNext = Observable.from(Arrays.asList(1, 2, 3));
        Observable<Integer> onCompleted = Observable.from(Arrays.asList(4));
        Observable<Integer> onError = Observable.from(Arrays.asList(5));

        Observable<Integer> source = Observable.from(Arrays.<Integer> asList());

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        source.flatMap(just(onNext), just(onError), funcThrow0(onCompleted)).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onCompleted();
    }

    @Test
    public void testFlatMapTransformsMergeException() {
        Observable<Integer> onNext = Observable.error(new TestException());
        Observable<Integer> onCompleted = Observable.from(Arrays.asList(4));
        Observable<Integer> onError = Observable.from(Arrays.asList(5));

        Observable<Integer> source = Observable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        source.flatMap(just(onNext), just(onError), funcThrow0(onCompleted)).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onCompleted();
    }

    private static <T> Observable<T> compose(Observable<T> source, final AtomicInteger subscriptionCount, final int m) {
        return source.doOnSubscribe(new Action0() {
            @Override
            public void call() {
                if (subscriptionCount.getAndIncrement() >= m) {
                    Assert.fail("Too many subscriptions! " + subscriptionCount.get());
                }
            }
        }).doOnCompleted(new Action0() {
            @Override
            public void call() {
                if (subscriptionCount.decrementAndGet() < 0) {
                    Assert.fail("Too many unsubscriptionss! " + subscriptionCount.get());
                }
            }
        });
    }

    @Test
    public void testFlatMapMaxConcurrent() {
        final int m = 4;
        final AtomicInteger subscriptionCount = new AtomicInteger();
        Observable<Integer> source = Observable.range(1, 10).flatMap(new Func1<Integer, Observable<Integer>>() {
            @Override
            public Observable<Integer> call(Integer t1) {
                return compose(Observable.range(t1 * 10, 2), subscriptionCount, m)
                        .subscribeOn(Schedulers.computation());
            }
        }, m);
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        source.subscribe(ts);
        
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        Set<Integer> expected = new HashSet<Integer>(Arrays.asList(
                10, 11, 20, 21, 30, 31, 40, 41, 50, 51, 60, 61, 70, 71, 80, 81, 90, 91, 100, 101
        ));
        Assert.assertEquals(expected.size(), ts.getOnNextEvents().size());
        Assert.assertTrue(expected.containsAll(ts.getOnNextEvents()));
    }
    @Test
    public void testFlatMapSelectorMaxConcurrent() {
        final int m = 4;
        final AtomicInteger subscriptionCount = new AtomicInteger();
        Observable<Integer> source = Observable.range(1, 10).flatMap(new Func1<Integer, Observable<Integer>>() {
            @Override
            public Observable<Integer> call(Integer t1) {
                return compose(Observable.range(t1 * 10, 2), subscriptionCount, m)
                        .subscribeOn(Schedulers.computation());
            }
        }, new Func2<Integer, Integer, Integer>() {
            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 * 1000 + t2;
            }
        }, m);
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        source.subscribe(ts);
        
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        Set<Integer> expected = new HashSet<Integer>(Arrays.asList(
                1010, 1011, 2020, 2021, 3030, 3031, 4040, 4041, 5050, 5051, 
                6060, 6061, 7070, 7071, 8080, 8081, 9090, 9091, 10100, 10101
        ));
        Assert.assertEquals(expected.size(), ts.getOnNextEvents().size());
        System.out.println("--> testFlatMapSelectorMaxConcurrent: " + ts.getOnNextEvents());
        Assert.assertTrue(expected.containsAll(ts.getOnNextEvents()));
    }
    @Test
    public void testFlatMapTransformsMaxConcurrentNormal() {
        final int m = 2;
        final AtomicInteger subscriptionCount = new AtomicInteger();
        Observable<Integer> onNext = 
                compose(Observable.from(Arrays.asList(1, 2, 3)).observeOn(Schedulers.computation()), subscriptionCount, m)
                .subscribeOn(Schedulers.computation());
        Observable<Integer> onCompleted = compose(Observable.from(Arrays.asList(4)), subscriptionCount, m)
                .subscribeOn(Schedulers.computation());
        Observable<Integer> onError = Observable.from(Arrays.asList(5));

        Observable<Integer> source = Observable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        TestSubscriber<Object> ts = new TestSubscriber<Object>(o);

        source.flatMap(just(onNext), just(onError), just0(onCompleted), m).subscribe(ts);
        
        ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
        ts.assertNoErrors();
        ts.assertTerminalEvent();

        verify(o, times(3)).onNext(1);
        verify(o, times(3)).onNext(2);
        verify(o, times(3)).onNext(3);
        verify(o).onNext(4);
        verify(o).onCompleted();

        verify(o, never()).onNext(5);
        verify(o, never()).onError(any(Throwable.class));
    }
}
