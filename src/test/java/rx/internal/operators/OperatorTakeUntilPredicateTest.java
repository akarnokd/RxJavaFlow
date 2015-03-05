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

import org.junit.Test;

import rx.Flow.Subscriber;
import rx.*;
import rx.exceptions.TestException;
import rx.subscribers.TestSubscriber;
;

public class OperatorTakeUntilPredicateTest {
    @Test
    public void takeEmpty() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        Observable.empty().takeUntil(e -> false).subscribe(ts);
        
        verify(o, never()).onNext(any());
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeAll() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        Observable.just(1, 2).takeUntil(e -> false).subscribe(ts);
        
        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeFirst() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        Observable.just(1, 2).takeUntil(e -> true).subscribe(ts);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeSome() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        Observable.just(1, 2, 3).takeUntil(t1 -> t1 == 2).subscribe(ts);
        
        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o, never()).onNext(3);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void functionThrows() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        Observable.just(1, 2, 3).takeUntil(v -> {
            throw new TestException("Forced failure");
        }).subscribe(ts);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o, never()).onNext(3);
        verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
    }
    @Test
    public void sourceThrows() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        Observable.just(1)
        .concatWith(Observable.<Integer>error(new TestException()))
        .concatWith(Observable.just(2))
        .takeUntil(e -> false).subscribe(ts);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
    }
    @Test
    public void backpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(5);
        
        Observable.range(1, 1000).takeUntil(e -> false).subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertValues(1, 2, 3, 4, 5);
        ts.assertNoComplete();
    }
}
