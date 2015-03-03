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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import rx.Flowable;
import rx.Observer;
import rx.functions.Supplier;
import rx.functions.Function;
import rx.internal.util.UtilityFunctions;

public class OperatorToMapTest {
    @Mock
    Observer<Object> objectObserver;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    Function<String, Integer> lengthFunc = new Function<String, Integer>() {
        @Override
        public Integer call(String t1) {
            return t1.length();
        }
    };
    Function<String, String> duplicate = new Function<String, String>() {
        @Override
        public String call(String t1) {
            return t1 + t1;
        }
    };

    @Test
    public void testToMap() {
        Flowable<String> source = Flowable.just("a", "bb", "ccc", "dddd");

        Flowable<Map<Integer, String>> mapped = source.toMap(lengthFunc);

        Map<Integer, String> expected = new HashMap<Integer, String>();
        expected.put(1, "a");
        expected.put(2, "bb");
        expected.put(3, "ccc");
        expected.put(4, "dddd");

        mapped.subscribe(objectObserver);

        verify(objectObserver, never()).onError(any(Throwable.class));
        verify(objectObserver, times(1)).onNext(expected);
        verify(objectObserver, times(1)).onComplete();
    }

    @Test
    public void testToMapWithValueSelector() {
        Flowable<String> source = Flowable.just("a", "bb", "ccc", "dddd");

        Flowable<Map<Integer, String>> mapped = source.toMap(lengthFunc, duplicate);

        Map<Integer, String> expected = new HashMap<Integer, String>();
        expected.put(1, "aa");
        expected.put(2, "bbbb");
        expected.put(3, "cccccc");
        expected.put(4, "dddddddd");

        mapped.subscribe(objectObserver);

        verify(objectObserver, never()).onError(any(Throwable.class));
        verify(objectObserver, times(1)).onNext(expected);
        verify(objectObserver, times(1)).onComplete();
    }

    @Test
    public void testToMapWithError() {
        Flowable<String> source = Flowable.just("a", "bb", "ccc", "dddd");

        Function<String, Integer> lengthFuncErr = new Function<String, Integer>() {
            @Override
            public Integer call(String t1) {
                if ("bb".equals(t1)) {
                    throw new RuntimeException("Forced Failure");
                }
                return t1.length();
            }
        };
        Flowable<Map<Integer, String>> mapped = source.toMap(lengthFuncErr);

        Map<Integer, String> expected = new HashMap<Integer, String>();
        expected.put(1, "a");
        expected.put(2, "bb");
        expected.put(3, "ccc");
        expected.put(4, "dddd");

        mapped.subscribe(objectObserver);

        verify(objectObserver, never()).onNext(expected);
        verify(objectObserver, never()).onComplete();
        verify(objectObserver, times(1)).onError(any(Throwable.class));

    }

    @Test
    public void testToMapWithErrorInValueSelector() {
        Flowable<String> source = Flowable.just("a", "bb", "ccc", "dddd");

        Function<String, String> duplicateErr = new Function<String, String>() {
            @Override
            public String call(String t1) {
                if ("bb".equals(t1)) {
                    throw new RuntimeException("Forced failure");
                }
                return t1 + t1;
            }
        };

        Flowable<Map<Integer, String>> mapped = source.toMap(lengthFunc, duplicateErr);

        Map<Integer, String> expected = new HashMap<Integer, String>();
        expected.put(1, "aa");
        expected.put(2, "bbbb");
        expected.put(3, "cccccc");
        expected.put(4, "dddddddd");

        mapped.subscribe(objectObserver);

        verify(objectObserver, never()).onNext(expected);
        verify(objectObserver, never()).onComplete();
        verify(objectObserver, times(1)).onError(any(Throwable.class));

    }

    @Test
    public void testToMapWithFactory() {
        Flowable<String> source = Flowable.just("a", "bb", "ccc", "dddd");

        Supplier<Map<Integer, String>> mapFactory = new Supplier<Map<Integer, String>>() {
            @Override
            public Map<Integer, String> call() {
                return new LinkedHashMap<Integer, String>() {
                    /** */
                    private static final long serialVersionUID = -3296811238780863394L;

                    @Override
                    protected boolean removeEldestEntry(Map.Entry<Integer, String> eldest) {
                        return size() > 3;
                    }
                };
            }
        };

        Function<String, Integer> lengthFunc = new Function<String, Integer>() {
            @Override
            public Integer call(String t1) {
                return t1.length();
            }
        };
        Flowable<Map<Integer, String>> mapped = source.toMap(lengthFunc, UtilityFunctions.<String>identity(), mapFactory);

        Map<Integer, String> expected = new LinkedHashMap<Integer, String>();
        expected.put(2, "bb");
        expected.put(3, "ccc");
        expected.put(4, "dddd");

        mapped.subscribe(objectObserver);

        verify(objectObserver, never()).onError(any(Throwable.class));
        verify(objectObserver, times(1)).onNext(expected);
        verify(objectObserver, times(1)).onComplete();
    }

    @Test
    public void testToMapWithErrorThrowingFactory() {
        Flowable<String> source = Flowable.just("a", "bb", "ccc", "dddd");

        Supplier<Map<Integer, String>> mapFactory = new Supplier<Map<Integer, String>>() {
            @Override
            public Map<Integer, String> call() {
                throw new RuntimeException("Forced failure");
            }
        };

        Function<String, Integer> lengthFunc = new Function<String, Integer>() {
            @Override
            public Integer call(String t1) {
                return t1.length();
            }
        };
        Flowable<Map<Integer, String>> mapped = source.toMap(lengthFunc, UtilityFunctions.<String>identity(), mapFactory);

        Map<Integer, String> expected = new LinkedHashMap<Integer, String>();
        expected.put(2, "bb");
        expected.put(3, "ccc");
        expected.put(4, "dddd");

        mapped.subscribe(objectObserver);

        verify(objectObserver, never()).onNext(expected);
        verify(objectObserver, never()).onComplete();
        verify(objectObserver, times(1)).onError(any(Throwable.class));
    }

}
