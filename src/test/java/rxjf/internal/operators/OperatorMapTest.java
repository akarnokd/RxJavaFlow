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
import java.util.function.*;

import org.junit.*;
import org.mockito.*;

import rxjf.Flow.Subscriber;
import rxjf.*;
import rxjf.exceptions.OnErrorNotImplementedException;
import rxjf.schedulers.Schedulers;

public class OperatorMapTest {

    @Mock
    Subscriber<String> stringObserver;
    @Mock
    Subscriber<String> stringObserver2;

    final static BiFunction<String, Integer, String> APPEND_INDEX = (value, index) -> value + index;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testMap() {
        Map<String, String> m1 = getMap("One");
        Map<String, String> m2 = getMap("Two");
        Flowable<Map<String, String>> observable = Flowable.from(m1, m2);

        Flowable<String> m = observable.map(map -> map.get("firstName"));
        m.subscribe(stringObserver);

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(1)).onNext("OneFirst");
        verify(stringObserver, times(1)).onNext("TwoFirst");
        verify(stringObserver, times(1)).onComplete();
    }

    @Test
    public void testMapMany() {
        /* simulate a top-level async call which returns IDs */
        Flowable<Integer> ids = Flowable.from(1, 2);

        /* now simulate the behavior to take those IDs and perform nested async calls based on them */
        Flowable<String> m = ids.flatMap(id -> {
            /* simulate making a nested async call which creates another Flowable */
            Flowable<Map<String, String>> subFlowable = null;
            if (id == 1) {
                Map<String, String> m1 = getMap("One");
                Map<String, String> m2 = getMap("Two");
                subFlowable = Flowable.from(m1, m2);
            } else {
                Map<String, String> m3 = getMap("Three");
                Map<String, String> m4 = getMap("Four");
                subFlowable = Flowable.from(m3, m4);
            }

            /* simulate kicking off the async call and performing a select on it to transform the data */
            return subFlowable.map(map -> map.get("firstName"));
        });
        
        m.subscribe(stringObserver);

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
        Flowable<Map<String, String>> observable1 = Flowable.from(m1, m2);

        Map<String, String> m3 = getMap("Three");
        Map<String, String> m4 = getMap("Four");
        Flowable<Map<String, String>> observable2 = Flowable.from(m3, m4);

        Flowable<Flowable<Map<String, String>>> observable = Flowable.from(observable1, observable2);

        Flowable<String> m = observable.flatMap( o -> o.map(map -> map.get("firstName")));
        
        m.subscribe(stringObserver);

        verify(stringObserver, never()).onError(any(Throwable.class));
        verify(stringObserver, times(1)).onNext("OneFirst");
        verify(stringObserver, times(1)).onNext("TwoFirst");
        verify(stringObserver, times(1)).onNext("ThreeFirst");
        verify(stringObserver, times(1)).onNext("FourFirst");
        verify(stringObserver, times(1)).onComplete();

    }

    @Test
    public void testMapWithError() {
        Flowable<String> w = Flowable.from("one", "fail", "two", "three", "fail");
        Flowable<String> m = w.map(s -> {
                if ("fail".equals(s)) {
                    throw new RuntimeException("Forced Failure");
                }
                return s;
            }
        ).doOnError(t1 -> t1.printStackTrace());

        m.subscribe(stringObserver);
        verify(stringObserver, times(1)).onNext("one");
        verify(stringObserver, never()).onNext("two");
        verify(stringObserver, never()).onNext("three");
        verify(stringObserver, never()).onComplete();
        verify(stringObserver, times(1)).onError(any(Throwable.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMapWithIssue417() {
        Flowable.just(1).observeOn(Schedulers.computation())
                .map(v -> { throw new IllegalArgumentException("any error"); }).toBlocking().single();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMapWithErrorInFuncAndThreadPoolScheduler() throws InterruptedException {
        // The error will throw in one of threads in the thread pool.
        // If map does not handle it, the error will disappear.
        // so map needs to handle the error by itself.
        Flowable<String> m = Flowable.just("one")
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
        Flowable.range(1, 0).last().map(i -> i).toBlocking().single();
    }

    /**
     * We expect IllegalStateException to pass thru map.
     */
    @Test(expected = IllegalStateException.class)
    public void testErrorPassesThruMap2() {
        Flowable.error(new IllegalStateException()).map(i -> i).toBlocking().single();
    }

    /**
     * We expect an ArithmeticException exception here because last() emits a single value
     * but then we divide by 0.
     */
    @Test(expected = ArithmeticException.class)
    public void testMapWithErrorInFunc() {
        Flowable.range(1, 1).last().map(i -> i / 0).toBlocking().single();
    }

    @Test(expected = OnErrorNotImplementedException.class)
    public void verifyExceptionIsThrownIfThereIsNoExceptionHandler() {

        Function<Object, Flowable<Object>> manyMapper = Flowable::just;
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
            Flowable.from("a", "b", "c")
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

    @Test(expected = OnErrorNotImplementedException.class)
    public void testShouldNotSwallowOnErrorNotImplementedException() {
        Flowable.from("a", "b").flatMap(s -> Flowable.from(s + "1", s + "2"))
        .flatMap(s -> Flowable.error(new Exception("test")))
        .forEach(System.out::println);
    }
}
