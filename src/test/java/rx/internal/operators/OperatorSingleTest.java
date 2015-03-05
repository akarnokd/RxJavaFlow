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
import static org.mockito.Mockito.*;

import java.util.NoSuchElementException;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flow.Subscriber;
import rx.*;
import rx.subscribers.AbstractSubscriber;

public class OperatorSingleTest {

    @Test
    public void testSingle() {
        Observable<Integer> observable = Observable.just(1).single();

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(1);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleWithTooManyElements() {
        Observable<Integer> observable = Observable.from(1, 2).single();

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(IllegalArgumentException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleWithEmpty() {
        Observable<Integer> observable = Observable.<Integer> empty().single();

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(NoSuchElementException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleWithPredicate() {
        Observable<Integer> observable = Observable.from(1, 2).single(
                t1 -> t1 % 2 == 0);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(2);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleWithPredicateAndTooManyElements() {
        Observable<Integer> observable = Observable.from(1, 2, 3, 4).single(
                t1 -> t1 % 2 == 0);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(IllegalArgumentException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleWithPredicateAndEmpty() {
        Observable<Integer> observable = Observable.just(1).single(
                t1 -> t1 % 2 == 0);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(NoSuchElementException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleOrDefault() {
        Observable<Integer> observable = Observable.just(1).singleOrDefault(2);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(1);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleOrDefaultWithTooManyElements() {
        Observable<Integer> observable = Observable.from(1, 2).singleOrDefault(
                3);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(IllegalArgumentException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleOrDefaultWithEmpty() {
        Observable<Integer> observable = Observable.<Integer> empty()
                .singleOrDefault(1);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(1);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleOrDefaultWithPredicate() {
        Observable<Integer> observable = Observable.from(1, 2).singleOrDefault(
                4, t1 -> t1 % 2 == 0);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(2);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleOrDefaultWithPredicateAndTooManyElements() {
        Observable<Integer> observable = Observable.from(1, 2, 3, 4)
                .singleOrDefault(6, t1 -> t1 % 2 == 0);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(
                isA(IllegalArgumentException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleOrDefaultWithPredicateAndEmpty() {
        Observable<Integer> observable = Observable.just(1).singleOrDefault(2,
                t1 -> t1 % 2 == 0);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(2);
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testSingleWithBackpressure() {
        Observable<Integer> observable = Observable.from(1, 2).single();

        Subscriber<Integer> subscriber = spy(new AbstractSubscriber<Integer>() {

            @Override
            protected void onSubscribe() {
                subscription.request(1);
            }
            
            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(Integer integer) {
                subscription.request(1);
            }
        });
        observable.subscribe(subscriber);

        InOrder inOrder = inOrder(subscriber);
        inOrder.verify(subscriber, times(1)).onError(isA(IllegalArgumentException.class));
        inOrder.verifyNoMoreInteractions();
    }

    @Test(timeout = 30000)
    public void testIssue1527() throws InterruptedException {
        //https://github.com/ReactiveX/RxJava/pull/1527
        Observable<Integer> source = Observable.from(1, 2, 3, 4, 5, 6);
        Observable<Integer> reduced = source.reduce((i1, i2) -> i1 + i2);

        Integer r = reduced.toBlocking().first();
        assertEquals(21, r.intValue());
    }
}
