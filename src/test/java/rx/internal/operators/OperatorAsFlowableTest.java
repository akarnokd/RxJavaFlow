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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import rx.Flow.Subscriber;
import rx.*;
import rx.exceptions.TestException;
import rx.subjects.PublishSubject;
import rx.subscribers.TestSubscriber;

public class OperatorAsObservableTest {
    @Test
    public void testHiding() {
        PublishSubject<Integer> src = PublishSubject.create();
        
        Observable<Integer> dst = src.asObservable();
        
        assertFalse(dst instanceof PublishSubject);
        
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        dst.subscribe(ts);
        
        src.onNext(1);
        src.onComplete();
        
        verify(o).onNext(1);
        verify(o).onComplete();
        verify(o, never()).onError(any(Throwable.class));
    }
    @Test
    public void testHidingError() {
        PublishSubject<Integer> src = PublishSubject.create();
        
        Observable<Integer> dst = src.asObservable();
        
        assertFalse(dst instanceof PublishSubject);
        
        @SuppressWarnings("unchecked")
        Subscriber<Object> o = mock(Subscriber.class);
        TestSubscriber<Object> ts = new TestSubscriber<>(o);
        
        dst.subscribe(ts);
        
        src.onError(new TestException());
        
        verify(o, never()).onNext(any());
        verify(o, never()).onComplete();
        verify(o).onError(any(TestException.class));
    }
    
    @Test
    public void backpressuredForwarded() {
        Observable<Integer> src = Observable.range(1, 10);
        Observable<Integer> source = src.asObservable();
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoErrors();
        
        ts.requestMore(2);
        
        ts.assertValues(1, 2);
        ts.assertNoTerminalEvent();
        
        ts.cancel();
        
        ts.requestMore(2);
        
        ts.assertValues(1, 2);
        ts.assertNoTerminalEvent();
    }
}
