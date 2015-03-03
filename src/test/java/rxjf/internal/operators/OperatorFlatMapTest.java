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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

import org.junit.*;

import rxjf.Flow.Subscriber;
import rxjf.*;
import rxjf.exceptions.TestException;
import rxjf.schedulers.Schedulers;
import rxjf.subscribers.TestSubscriber;

public class OperatorFlatMapTest {
    @Test
    public void testNormal() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        final List<Integer> list = Arrays.asList(1, 2, 3);

        Function<Integer, List<Integer>> func = t -> list;
        BiFunction<Integer, Integer, Integer> resFunc = (t1, t2) -> t1 | t2;
                ;
        List<Integer> source = Arrays.asList(16, 32, 64);

        Flowable.from(source).flatMapIterable(func, resFunc).subscribe(o);

        for (Integer s : source) {
            for (Integer v : list) {
                verify(o).onNext(s | v);
            }
        }
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testCollectionFunctionThrows() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        Function<Integer, List<Integer>> func = t1 -> { throw new TestException(); };
        BiFunction<Integer, Integer, Integer> resFunc = (t1, t2) -> t1 | t2;

        List<Integer> source = Arrays.asList(16, 32, 64);

        Flowable.from(source).flatMapIterable(func, resFunc).subscribe(o);

        verify(o, never()).onComplete();
        verify(o, never()).onNext(any());
        verify(o).onError(any(TestException.class));
    }

    @Test
    public void testResultFunctionThrows() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        final List<Integer> list = Arrays.asList(1, 2, 3);

        Function<Integer, List<Integer>> func = new Function<Integer, List<Integer>>() {
            @Override
            public List<Integer> call(Integer t1) {
                return list;
            }
        };
        BiFunction<Integer, Integer, Integer> resFunc = new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                throw new TestException();
            }
        };

        List<Integer> source = Arrays.asList(16, 32, 64);

        Flowable.from(source).flatMapIterable(func, resFunc).subscribe(o);

        verify(o, never()).onComplete();
        verify(o, never()).onNext(any());
        verify(o).onError(any(TestException.class));
    }

    @Test
    public void testMergeError() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        Function<Integer, Flowable<Integer>> func = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Integer t1) {
                return Flowable.error(new TestException());
            }
        };
        BiFunction<Integer, Integer, Integer> resFunc = new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 | t2;
            }
        };

        List<Integer> source = Arrays.asList(16, 32, 64);

        Flowable.from(source).flatMap(func, resFunc).subscribe(o);

        verify(o, never()).onComplete();
        verify(o, never()).onNext(any());
        verify(o).onError(any(TestException.class));
    }

    <T, R> Function<T, R> just(final R value) {
        return new Function<T, R>() {

            @Override
            public R call(T t1) {
                return value;
            }
        };
    }

    <R> Supplier<R> just0(final R value) {
        return new Supplier<R>() {

            @Override
            public R call() {
                return value;
            }
        };
    }

    @Test
    public void testFlatMapTransformsNormal() {
        Flowable<Integer> onNext = Flowable.from(Arrays.asList(1, 2, 3));
        Flowable<Integer> onComplete() = Flowable.from(Arrays.asList(4));
        Flowable<Integer> onError = Flowable.from(Arrays.asList(5));

        Flowable<Integer> source = Flowable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        source.flatMap(just(onNext), just(onError), just0(onComplete())).subscribe(o);

        verify(o, times(3)).onNext(1);
        verify(o, times(3)).onNext(2);
        verify(o, times(3)).onNext(3);
        verify(o).onNext(4);
        verify(o).onComplete();

        verify(o, never()).onNext(5);
        verify(o, never()).onError(any(Throwable.class));
    }

    @Test
    public void testFlatMapTransformsException() {
        Flowable<Integer> onNext = Flowable.from(Arrays.asList(1, 2, 3));
        Flowable<Integer> onComplete() = Flowable.from(Arrays.asList(4));
        Flowable<Integer> onError = Flowable.from(Arrays.asList(5));

        Flowable<Integer> source = Flowable.concat(
                Flowable.from(Arrays.asList(10, 20, 30)),
                Flowable.<Integer> error(new RuntimeException("Forced failure!"))
                );
        

        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        source.flatMap(just(onNext), just(onError), just0(onComplete())).subscribe(o);

        verify(o, times(3)).onNext(1);
        verify(o, times(3)).onNext(2);
        verify(o, times(3)).onNext(3);
        verify(o).onNext(5);
        verify(o).onComplete();
        verify(o, never()).onNext(4);

        verify(o, never()).onError(any(Throwable.class));
    }

    <R> Supplier<R> funcThrow0(R r) {
        return new Supplier<R>() {
            @Override
            public R call() {
                throw new TestException();
            }
        };
    }

    <T, R> Function<T, R> funcThrow(T t, R r) {
        return new Function<T, R>() {
            @Override
            public R call(T t) {
                throw new TestException();
            }
        };
    }

    @Test
    public void testFlatMapTransformsOnNextFuncThrows() {
        Flowable<Integer> onComplete() = Flowable.from(Arrays.asList(4));
        Flowable<Integer> onError = Flowable.from(Arrays.asList(5));

        Flowable<Integer> source = Flowable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        source.flatMap(funcThrow(1, onError), just(onError), just0(onComplete())).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testFlatMapTransformsOnErrorFuncThrows() {
        Flowable<Integer> onNext = Flowable.from(Arrays.asList(1, 2, 3));
        Flowable<Integer> onComplete() = Flowable.from(Arrays.asList(4));
        Flowable<Integer> onError = Flowable.from(Arrays.asList(5));

        Flowable<Integer> source = Flowable.error(new TestException());

        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        source.flatMap(just(onNext), funcThrow((Throwable) null, onError), just0(onComplete())).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testFlatMapTransformsonComplete()FuncThrows() {
        Flowable<Integer> onNext = Flowable.from(Arrays.asList(1, 2, 3));
        Flowable<Integer> onComplete() = Flowable.from(Arrays.asList(4));
        Flowable<Integer> onError = Flowable.from(Arrays.asList(5));

        Flowable<Integer> source = Flowable.from(Arrays.<Integer> asList());

        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        source.flatMap(just(onNext), just(onError), funcThrow0(onComplete())).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    @Test
    public void testFlatMapTransformsMergeException() {
        Flowable<Integer> onNext = Flowable.error(new TestException());
        Flowable<Integer> onComplete() = Flowable.from(Arrays.asList(4));
        Flowable<Integer> onError = Flowable.from(Arrays.asList(5));

        Flowable<Integer> source = Flowable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);

        source.flatMap(just(onNext), just(onError), funcThrow0(onComplete())).subscribe(o);

        verify(o).onError(any(TestException.class));
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
    }

    private static <T> Flowable<T> compose(Flowable<T> source, final AtomicInteger subscriptionCount, final int m) {
        return source.doOnSubscribe(new Action0() {
            @Override
            public void call() {
                if (subscriptionCount.getAndIncrement() >= m) {
                    Assert.fail("Too many subscriptions! " + subscriptionCount.get());
                }
            }
        }).doOnComplete(new Action0() {
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
        Flowable<Integer> source = Flowable.range(1, 10).flatMap(new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Integer t1) {
                return compose(Flowable.range(t1 * 10, 2), subscriptionCount, m)
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
        Flowable<Integer> source = Flowable.range(1, 10).flatMap(new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Integer t1) {
                return compose(Flowable.range(t1 * 10, 2), subscriptionCount, m)
                        .subscribeOn(Schedulers.computation());
            }
        }, new BiFunction<Integer, Integer, Integer>() {
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
        Flowable<Integer> onNext = 
                compose(Flowable.from(Arrays.asList(1, 2, 3)).observeOn(Schedulers.computation()), subscriptionCount, m)
                .subscribeOn(Schedulers.computation());
        Flowable<Integer> onComplete() = compose(Flowable.from(Arrays.asList(4)), subscriptionCount, m)
                .subscribeOn(Schedulers.computation());
        Flowable<Integer> onError = Flowable.from(Arrays.asList(5));

        Flowable<Integer> source = Flowable.from(Arrays.asList(10, 20, 30));

        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<Object>(o);

        source.flatMap(just(onNext), just(onError), just0(onComplete()), m).subscribe(ts);
        
        ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
        ts.assertNoErrors();
        ts.assertTerminalEvent();

        verify(o, times(3)).onNext(1);
        verify(o, times(3)).onNext(2);
        verify(o, times(3)).onNext(3);
        verify(o).onNext(4);
        verify(o).onComplete();

        verify(o, never()).onNext(5);
        verify(o, never()).onError(any(Throwable.class));
    }
}
