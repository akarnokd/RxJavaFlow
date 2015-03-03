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
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import rx.*;
import rx.functions.Function;

public class OperatorAllTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testAll() {
        Flowable<String> obs = Flowable.just("one", "two", "six");

        Observer<Boolean> observer = mock(Observer.class);
        obs.all(new Function<String, Boolean>() {
            @Override
            public Boolean call(String s) {
                return s.length() == 3;
            }
        }).subscribe(observer);

        verify(observer).onNext(true);
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNotAll() {
        Flowable<String> obs = Flowable.just("one", "two", "three", "six");

        Observer<Boolean> observer = mock(Observer.class);
        obs.all(new Function<String, Boolean>() {
            @Override
            public Boolean call(String s) {
                return s.length() == 3;
            }
        }).subscribe(observer);

        verify(observer).onNext(false);
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testEmpty() {
        Flowable<String> obs = Flowable.empty();

        Observer<Boolean> observer = mock(Observer.class);
        obs.all(new Function<String, Boolean>() {
            @Override
            public Boolean call(String s) {
                return s.length() == 3;
            }
        }).subscribe(observer);

        verify(observer).onNext(true);
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testError() {
        Throwable error = new Throwable();
        Flowable<String> obs = Flowable.error(error);

        Observer<Boolean> observer = mock(Observer.class);
        obs.all(new Function<String, Boolean>() {
            @Override
            public Boolean call(String s) {
                return s.length() == 3;
            }
        }).subscribe(observer);

        verify(observer).onError(error);
        verifyNoMoreInteractions(observer);
    }

    @Test
    public void testFollowingFirst() {
        Flowable<Integer> o = Flowable.from(Arrays.asList(1, 3, 5, 6));
        Flowable<Boolean> allOdd = o.all(new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer i) {
                return i % 2 == 1;
            }
        });
        assertFalse(allOdd.toBlocking().first());
    }
    @Test(timeout = 5000)
    public void testIssue1935NoUnsubscribeDownstream() {
        Flowable<Integer> source = Flowable.just(1)
            .all(new Function<Object, Boolean>() {
                @Override
                public Boolean call(Object t1) {
                    return false;
                }
            })
            .flatMap(new Function<Boolean, Flowable<Integer>>() {
                @Override
                public Flowable<Integer> call(Boolean t1) {
                    return Flowable.just(2).delay(500, TimeUnit.MILLISECONDS);
                }
        });
        assertEquals((Object)2, source.toBlocking().first());
    }
}
