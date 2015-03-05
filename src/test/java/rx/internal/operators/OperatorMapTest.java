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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.function.*;

import org.junit.*;

import rx.Flow.Subscriber;
import rx.*;
import rx.exceptions.OnErrorNotImplementedException;
import rx.schedulers.Schedulers;
import rx.subscribers.TestSubscriber;

public class OperatorMapTest {

    final static BiFunction<String, Integer, String> APPEND_INDEX = (value, index) -> value + index;

    @Test
    public void testMap() {
        Map<String, String> m1 = getMap("One");
        Map<String, String> m2 = getMap("Two");
        Observable<Map<String, String>> observable = Observable.from(m1, m2);

        Observable<String> m = observable.map(map -> map.get("firstName"));
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(1)).onNext("OneFirst");
        verify(stringObserver, times(1)).onNext("TwoFirst");
        verify(stringObserver, times(1)).onComplete();
    }

    @Test
    public void testMapMany() {
        /* simulate a top-level async call which returns IDs */
        Observable<Integer> ids = Observable.from(1, 2);

        /* now simulate the behavior to take those IDs and perform nested async calls based on them */
        Observable<String> m = ids.flatMap(id -> {
            /* simulate making a nested async call which creates another Observable */
            Observable<Map<String, String>> subObservable = null;
            if (id == 1) {
                Map<String, String> m1 = getMap("One");
                Map<String, String> m2 = getMap("Two");
                subObservable = Observable.from(m1, m2);
            } else {
                Map<String, String> m3 = getMap("Three");
                Map<String, String> m4 = getMap("Four");
                subObservable = Observable.from(m3, m4);
            }

            /* simulate kicking off the async call and performing a select on it to transform the data */
            return subObservable.map(map -> map.get("firstName"));
        });
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        
        m.subscribe(ts);

        ts.assertSubscription();
        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(1)).onNext("OneFirst");
        verify(stringObserver, times(1)).onNext("TwoFirst");
        verify(stringObserver, times(1)).onNext("ThreeFirst");
        verify(stringObserver, times(1)).onNext("FourFirst");
        verify(stringObserver, times(1)).onComplete();
    }

    @Test
    public void testMapMany2() {
        Map<String, String> m1 = getMap("One");
        Map<String, String> m2 = getMap("Two");
        Observable<Map<String, String>> observable1 = Observable.from(m1, m2);

        Map<String, String> m3 = getMap("Three");
        Map<String, String> m4 = getMap("Four");
        Observable<Map<String, String>> observable2 = Observable.from(m3, m4);

        Observable<Observable<Map<String, String>>> observable = Observable.from(observable1, observable2);

        Observable<String> m = observable.flatMap( o -> o.map(map -> map.get("firstName")));
        
        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);

        m.subscribe(ts);

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(1)).onNext("OneFirst");
        verify(stringObserver, times(1)).onNext("TwoFirst");
        verify(stringObserver, times(1)).onNext("ThreeFirst");
        verify(stringObserver, times(1)).onNext("FourFirst");
        verify(stringObserver, times(1)).onComplete();

    }

    @Test
    public void testMapWithError() {
        Observable<String> w = Observable.from("one", "fail", "two", "three", "fail");
        Observable<String> m = w.map(s -> {
                if ("fail".equals(s)) {
                    throw new RuntimeException("Forced Failure");
                }
                return s;
            }
        ).doOnError(t1 -> t1.printStackTrace());

        @SuppressWarnings("unchecked")
        Subscriber<String> stringObserver = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(stringObserver);
        m.subscribe(ts);
        
        verify(stringObserver, times(1)).onNext("one");
        verify(stringObserver, never()).onNext("two");
        verify(stringObserver, never()).onNext("three");
        verify(stringObserver, never()).onComplete();
        verify(stringObserver, times(1)).onError(any(Throwable.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMapWithIssue417() {
        Observable.just(1).observeOn(Schedulers.computation())
                .map(v -> { throw new IllegalArgumentException("any error"); })
                .toBlocking().single();
    }

    @Test(timeout = 1000, expected = IllegalArgumentException.class)
    public void testMapWithErrorInFuncAndThreadPoolScheduler() throws InterruptedException {
        // The error will throw in one of threads in the thread pool.
        // If map does not handle it, the error will disappear.
        // so map needs to handle the error by itself.
        Observable<String> m = Observable.just("one")
                .observeOn(Schedulers.computation())
                .map(v -> {
                        throw new IllegalArgumentException("any error");
                });

        // block for response, expecting exception thrown
        m.toBlocking().last();
    }

    /**
     * While mapping over range(1,0).last() we expect NoSuchElementException since the sequence is empty.
     */
    @Test(expected = NoSuchElementException.class)
    public void testErrorPassesThruMap() {
        Observable.range(1, 0).last().map(i -> i)
        .toBlocking().single();
    }

    /**
     * We expect IllegalStateException to pass thru map.
     */
    @Test(expected = IllegalStateException.class)
    public void testErrorPassesThruMap2() {
        Observable.error(new IllegalStateException()).map(i -> i).toBlocking().single();
    }

    /**
     * We expect an ArithmeticException exception here because last() emits a single value
     * but then we divide by 0.
     */
    @Test(expected = ArithmeticException.class)
    public void testMapWithErrorInFunc() {
        Observable.range(1, 1).last().map(i -> i / 0).toBlocking().single();
    }

    @Ignore // TODO Reactive-Streams spec prohibits throwing
    @Test(expected = OnErrorNotImplementedException.class)
    public void verifyExceptionIsThrownIfThereIsNoExceptionHandler() {

        Function<Object, Observable<Object>> manyMapper = Observable::just;
        Function<Object, Object> mapper = new Function<Object, Object>() {
            private int count = 0;

            @Override
            public Object apply(Object object) {
                ++count;
                if (count > 2) {
                    throw new RuntimeException();
                }
                return object;
            }
        };

        try {
            Observable.from("a", "b", "c")
            .flatMap(manyMapper).map(mapper)
            .subscribe(System.out::println);
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private static Map<String, String> getMap(String prefix) {
        Map<String, String> m = new HashMap<>();
        m.put("firstName", prefix + "First");
        m.put("lastName", prefix + "Last");
        return m;
    }

    @Ignore // Reactive-Streams spec prohibits throwing 
    @Test(expected = OnErrorNotImplementedException.class)
    public void testShouldNotSwallowOnErrorNotImplementedException() {
        Observable.from("a", "b").flatMap(s -> Observable.from(s + "1", s + "2"))
        .flatMap(s -> Observable.error(new Exception("test")))
        .forEach(System.out::println);
    }
}
