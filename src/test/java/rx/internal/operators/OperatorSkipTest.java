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
import rx.subscribers.TestSubscriber;
import rx.*;

public class OperatorSkipTest {

    @Test
    public void testSkipNegativeElements() {

        Observable<String> skip = Observable.just("one", "two", "three").skip(-99);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        
        skip.subscribe(ts);
        
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipZeroElements() {

        Observable<String> skip = Observable.just("one", "two", "three").skip(0);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        
        skip.subscribe(ts);
        
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipOneElement() {

        Observable<String> skip = Observable.just("one", "two", "three").skip(1);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        
        skip.subscribe(ts);
        
        verify(observer, never()).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipTwoElements() {

        Observable<String> skip = Observable.just("one", "two", "three").skip(2);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        
        skip.subscribe(ts);
        
        verify(observer, never()).onNext("one");
        verify(observer, never()).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipEmptyStream() {

        Observable<String> w = Observable.empty();
        Observable<String> skip = w.skip(1);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(observer);
        
        skip.subscribe(ts);
        
        verify(observer, never()).onNext(any(String.class));
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipMultipleSubscribers() {

        Observable<String> skip = Observable.just("one", "two", "three").skip(2);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer1 = mock(Subscriber.class);
        TestSubscriber<String> ts1 = new TestSubscriber<>(observer1);
        
        skip.subscribe(ts1);
        

        @SuppressWarnings("unchecked")
        Subscriber<String> observer2 = mock(Subscriber.class);
        TestSubscriber<String> ts2 = new TestSubscriber<>(observer2);
        
        skip.subscribe(ts2);
        

        verify(observer1, times(1)).onNext(any(String.class));
        verify(observer1, never()).onError(any(Throwable.class));
        verify(observer1, times(1)).onComplete();

        verify(observer2, times(1)).onNext(any(String.class));
        verify(observer2, never()).onError(any(Throwable.class));
        verify(observer2, times(1)).onComplete();
    }

    @Test
    public void testSkipError() {

        Exception e = new Exception();

        Observable<String> ok = Observable.just("one");
        Observable<String> error = Observable.error(e);

        Observable<String> skip = Observable.concat(ok, error).skip(100);

        @SuppressWarnings("unchecked")
        Subscriber<String> observer = mock(Subscriber.class);
        skip.subscribe(observer);

        verify(observer, never()).onNext(any(String.class));
        verify(observer, times(1)).onError(e);
        verify(observer, never()).onComplete();

    }
    
    @Test
    public void backpressureNoRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        Observable<Integer> source = Observable.range(0, 1000).skip(5);
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNoComplete();
    }
    @Test
    public void skipAll() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Observable<Integer> source = Observable.range(0, 1000).skip(1000);
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();
    }
    @Test
    public void backpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        Observable<Integer> source = Observable.range(0, 1000).skip(5);
        
        source.subscribe(ts);
        
        ts.assertSubscription();
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNoComplete();
        
        ts.requestMore(5);
        
        ts.assertValues(5, 6, 7, 8, 9);
        ts.assertNoErrors();
        ts.assertNoComplete();
    }
}
