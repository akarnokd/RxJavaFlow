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
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import java.util.NoSuchElementException;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flowable;
import rx.Observer;
import rx.functions.Function;

public class OperatorLastTest {

    @Test
    public void testLastWithElements() {
        Flowable<Integer> last = Flowable.just(1, 2, 3).last();
        assertEquals(3, last.toBlocking().single().intValue());
    }

    @Test(expected = NoSuchElementException.class)
    public void testLastWithNoElements() {
        Flowable<?> last = Flowable.empty().last();
        last.toBlocking().single();
    }

    @Test
    public void testLastMultiSubscribe() {
        Flowable<Integer> last = Flowable.just(1, 2, 3).last();
        assertEquals(3, last.toBlocking().single().intValue());
        assertEquals(3, last.toBlocking().single().intValue());
    }

    @Test
    public void testLastViaFlowable() {
        Flowable.just(1, 2, 3).last();
    }

    @Test
    public void testLast() {
        Flowable<Integer> observable = Flowable.just(1, 2, 3).last();

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(3);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastWithOneElement() {
        Flowable<Integer> observable = Flowable.just(1).last();

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(1);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastWithEmpty() {
        Flowable<Integer> observable = Flowable.<Integer> empty().last();

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(NoSuchElementException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastWithPredicate() {
        Flowable<Integer> observable = Flowable.just(1, 2, 3, 4, 5, 6)
                .last(new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 % 2 == 0;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(6);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastWithPredicateAndOneElement() {
        Flowable<Integer> observable = Flowable.just(1, 2).last(
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 % 2 == 0;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(2);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastWithPredicateAndEmpty() {
        Flowable<Integer> observable = Flowable.just(1).last(
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 % 2 == 0;
                    }
                });
        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(NoSuchElementException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastOrDefault() {
        Flowable<Integer> observable = Flowable.just(1, 2, 3)
                .lastOrDefault(4);

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(3);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastOrDefaultWithOneElement() {
        Flowable<Integer> observable = Flowable.just(1).lastOrDefault(2);

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(1);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastOrDefaultWithEmpty() {
        Flowable<Integer> observable = Flowable.<Integer> empty()
                .lastOrDefault(1);

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(1);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastOrDefaultWithPredicate() {
        Flowable<Integer> observable = Flowable.just(1, 2, 3, 4, 5, 6)
                .lastOrDefault(8, new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 % 2 == 0;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(6);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastOrDefaultWithPredicateAndOneElement() {
        Flowable<Integer> observable = Flowable.just(1, 2).lastOrDefault(4,
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 % 2 == 0;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(2);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testLastOrDefaultWithPredicateAndEmpty() {
        Flowable<Integer> observable = Flowable.just(1).lastOrDefault(2,
                new Function<Integer, Boolean>() {

                    @Override
                    public Boolean call(Integer t1) {
                        return t1 % 2 == 0;
                    }
                });

        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(2);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
}
