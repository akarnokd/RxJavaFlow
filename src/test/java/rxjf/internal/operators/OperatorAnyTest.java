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
import rx.internal.util.UtilityFunctions;

public class OperatorAnyTest {

    @Test
    public void testAnyWithTwoItems() {
        Flowable<Integer> w = Flowable.just(1, 2);
        Flowable<Boolean> observable = w.exists(UtilityFunctions.alwaysTrue());

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, never()).onNext(false);
        verify(observer, times(1)).onNext(true);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testIsEmptyWithTwoItems() {
        Flowable<Integer> w = Flowable.just(1, 2);
        Flowable<Boolean> observable = w.isEmpty();

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, never()).onNext(true);
        verify(observer, times(1)).onNext(false);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testAnyWithOneItem() {
        Flowable<Integer> w = Flowable.just(1);
        Flowable<Boolean> observable = w.exists(UtilityFunctions.alwaysTrue());

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, never()).onNext(false);
        verify(observer, times(1)).onNext(true);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testIsEmptyWithOneItem() {
        Flowable<Integer> w = Flowable.just(1);
        Flowable<Boolean> observable = w.isEmpty();

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, never()).onNext(true);
        verify(observer, times(1)).onNext(false);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testAnyWithEmpty() {
        Flowable<Integer> w = Flowable.empty();
        Flowable<Boolean> observable = w.exists(UtilityFunctions.alwaysTrue());

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext(false);
        verify(observer, never()).onNext(true);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testIsEmptyWithEmpty() {
        Flowable<Integer> w = Flowable.empty();
        Flowable<Boolean> observable = w.isEmpty();

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext(true);
        verify(observer, never()).onNext(false);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testAnyWithPredicate1() {
        Flowable<Integer> w = Flowable.just(1, 2, 3);
        Flowable<Boolean> observable = w.exists(
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 < 2;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, never()).onNext(false);
        verify(observer, times(1)).onNext(true);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testExists1() {
        Flowable<Integer> w = Flowable.just(1, 2, 3);
        Flowable<Boolean> observable = w.exists(
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 < 2;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, never()).onNext(false);
        verify(observer, times(1)).onNext(true);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testAnyWithPredicate2() {
        Flowable<Integer> w = Flowable.just(1, 2, 3);
        Flowable<Boolean> observable = w.exists(
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 < 1;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext(false);
        verify(observer, never()).onNext(true);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testAnyWithEmptyAndPredicate() {
        // If the source is empty, always output false.
        Flowable<Integer> w = Flowable.empty();
        Flowable<Boolean> observable = w.exists(
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return true;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext(false);
        verify(observer, never()).onNext(true);
        verify(observer, never()).onError(org.mockito.Matchers.any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testWithFollowingFirst() {
        Flowable<Integer> o = Flowable.from(Arrays.asList(1, 3, 5, 6));
        Flowable<Boolean> anyEven = o.exists(new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer i) {
                return i % 2 == 0;
            }
        });
        assertTrue(anyEven.toBlocking().first());
    }
    @Test(timeout = 5000)
    public void testIssue1935NoUnsubscribeDownstream() {
        Flowable<Integer> source = Flowable.just(1).isEmpty()
            .flatMap(new Function<Boolean, Flowable<Integer>>() {
                @Override
                public Flowable<Integer> call(Boolean t1) {
                    return Flowable.just(2).delay(500, TimeUnit.MILLISECONDS);
                }
        });
        assertEquals((Object)2, source.toBlocking().first());
    }
}
