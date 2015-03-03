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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.mockito.Mockito;

import rx.Flowable;
import rx.Observer;
import rx.functions.BiFunction;
import rx.internal.operators.OperatorToFlowableSortedList;

public class OperatorToFlowableSortedListTest {

    @Test
    public void testSortedList() {
        Flowable<Integer> w = Flowable.just(1, 3, 2, 5, 4);
        Flowable<List<Integer>> observable = w.lift(new OperatorToFlowableSortedList<Integer>());

        @SuppressWarnings("unchecked")
        Observer<List<Integer>> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext(Arrays.asList(1, 2, 3, 4, 5));
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSortedListWithCustomFunction() {
        Flowable<Integer> w = Flowable.just(1, 3, 2, 5, 4);
        Flowable<List<Integer>> observable = w.lift(new OperatorToFlowableSortedList<Integer>(new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t2 - t1;
            }

        }));

        @SuppressWarnings("unchecked")
        Observer<List<Integer>> observer = mock(Observer.class);
        observable.subscribe(observer);
        verify(observer, times(1)).onNext(Arrays.asList(5, 4, 3, 2, 1));
        verify(observer, Mockito.never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testWithFollowingFirst() {
        Flowable<Integer> o = Flowable.just(1, 3, 2, 5, 4);
        assertEquals(Arrays.asList(1, 2, 3, 4, 5), o.toSortedList().toBlocking().first());
    }
}
