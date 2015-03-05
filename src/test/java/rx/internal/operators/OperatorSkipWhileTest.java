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

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.function.Predicate;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flow.Subscriber;
import rx.*;
import rx.subscribers.TestSubscriber;

public class OperatorSkipWhileTest {

    private static final Predicate<Integer> LESS_THAN_FIVE = v -> {
        if (v == 42) {
            throw new RuntimeException("that's not the answer to everything!");
        }
        return v < 5;
    };

    final Predicate<Integer> INDEX_LESS_THAN_THREE = new Predicate<Integer>() {
        int index = 0;
        @Override
        public boolean test(Integer value) {
            return index++ < 3;
        }
    };

    @Test
    public void testSkipWithIndex() {
        Observable<Integer> src = Observable.just(1, 2, 3, 4, 5);

        @SuppressWarnings("unchecked")
        Subscriber<Integer> w = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(w);
        
        src.skipWhile(INDEX_LESS_THAN_THREE).subscribe(ts);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext(4);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipEmpty() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> w = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(w);
        
        Observable<Integer> src = Observable.empty();
        
        src.skipWhile(LESS_THAN_FIVE).subscribe(ts);
        
        verify(w, never()).onNext(anyInt());
        verify(w, never()).onError(any(Throwable.class));
        verify(w, times(1)).onComplete();
    }

    @Test
    public void testSkipEverything() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> w = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(w);

        Observable<Integer> src = Observable.just(1, 2, 3, 4, 3, 2, 1);
        
        src.skipWhile(LESS_THAN_FIVE).subscribe(ts);
        
        verify(w, never()).onNext(anyInt());
        verify(w, never()).onError(any(Throwable.class));
        verify(w, times(1)).onComplete();
    }

    @Test
    public void testSkipNothing() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> w = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(w);

        Observable<Integer> src = Observable.just(5, 3, 1);
        
        src.skipWhile(LESS_THAN_FIVE).subscribe(ts);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onNext(3);
        inOrder.verify(w, times(1)).onNext(1);
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipSome() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> w = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(w);

        Observable<Integer> src = Observable.just(1, 2, 3, 4, 5, 3, 1, 5);
        
        src.skipWhile(LESS_THAN_FIVE).subscribe(ts);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onNext(3);
        inOrder.verify(w, times(1)).onNext(1);
        inOrder.verify(w, times(1)).onNext(5);
        inOrder.verify(w, times(1)).onComplete();
        inOrder.verify(w, never()).onError(any(Throwable.class));
    }

    @Test
    public void testSkipError() {
        @SuppressWarnings("unchecked")
        Subscriber<Integer> w = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(w);

        Observable<Integer> src = Observable.just(1, 2, 42, 5, 3, 1);
        
        src.skipWhile(LESS_THAN_FIVE).subscribe(ts);

        InOrder inOrder = inOrder(w);
        inOrder.verify(w, never()).onNext(anyInt());
        inOrder.verify(w, never()).onComplete();
        inOrder.verify(w, times(1)).onError(any(RuntimeException.class));
    }
    
    @Test
    public void testSkipManySubscribers() {
        Observable<Integer> src = Observable.range(1, 10).skipWhile(LESS_THAN_FIVE);
        int n = 5;
        for (int i = 0; i < n; i++) {
            @SuppressWarnings("unchecked")
            Subscriber<Integer> o = mock(Subscriber.class);
            TestSubscriber<Integer> ts = new TestSubscriber<>(o);

            InOrder inOrder = inOrder(o);
            
            src.subscribe(ts);
            
            for (int j = 5; j < 10; j++) {
                inOrder.verify(o).onNext(j);
            } 
            inOrder.verify(o).onComplete();
            verify(o, never()).onError(any(Throwable.class));
        }
    }
    
    @Test
    public void backpressureSkipSome() {
        Observable<Integer> source = Observable.range(1, 10).skipWhile(n -> n < 5);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoTerminalEvent();
        
        ts.requestMore(2);
        
        ts.assertValues(5, 6);
        ts.assertNoTerminalEvent();
        
        ts.requestMore(10);
        ts.assertValues(5, 6, 7, 8, 9, 10);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    @Test
    public void backpressureSkipAll() {
        Observable<Integer> source = Observable.range(1, 10).skipWhile(n -> true);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoTerminalEvent();
        
        ts.requestMore(2);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();
    }
    @Test
    public void backpressureSkipNone() {
        Observable<Integer> source = Observable.range(1, 10).skipWhile(n -> false);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoTerminalEvent();
        
        ts.requestMore(2);

        ts.assertValues(1, 2);
        ts.assertNoTerminalEvent();
        
        ts.requestMore(10);
        
        ts.assertValues(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        ts.assertNoErrors();
        ts.assertComplete();
    }
    
    public void cancelBeforeSkip() {
        Observable<Integer> source = Observable.range(1, 10).skipWhile(n -> false);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        source.subscribe(ts);
        
        ts.cancel();
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoTerminalEvent();

    }
    public void cancelAfterSkip() {
        Observable<Integer> source = Observable.range(1, 10).skipWhile(n -> n < 5);
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0) {
            @Override
            public void onNext(Integer item) {
                super.onNext(item);
                if (item == 6) {
                    subscription().cancel();
                }
            }
        };
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoTerminalEvent();

        ts.requestMore(10);
        
        ts.assertValues(5, 6);
        ts.assertNoErrors();
        ts.assertNoComplete();
    }
}
