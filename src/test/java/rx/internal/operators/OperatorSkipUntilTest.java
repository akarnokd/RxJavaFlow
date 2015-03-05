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

import org.junit.*;
import org.mockito.MockitoAnnotations;

import rx.Flow.Subscriber;
import rx.*;
import rx.subjects.PublishSubject;
import rx.subscribers.TestSubscriber;

public class OperatorSkipUntilTest {

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void normal1() {
        PublishSubject<Integer> source = PublishSubject.create();
        PublishSubject<Integer> other = PublishSubject.create();

        Observable<Integer> m = source.skipUntil(other);
        
        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);
        
        m.subscribe(ts);

        source.onNext(0);
        source.onNext(1);

        other.onNext(100);

        source.onNext(2);
        source.onNext(3);
        source.onNext(4);
        source.onComplete();

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onNext(2);
        verify(observer, times(1)).onNext(3);
        verify(observer, times(1)).onNext(4);
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void otherNeverFires() {
        PublishSubject<Integer> source = PublishSubject.create();

        Observable<Integer> m = source.skipUntil(Observable.never());

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);

        m.subscribe(ts);

        source.onNext(0);
        source.onNext(1);
        source.onNext(2);
        source.onNext(3);
        source.onNext(4);
        source.onComplete();

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onNext(any());
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void otherEmpty() {
        PublishSubject<Integer> source = PublishSubject.create();

        Observable<Integer> m = source.skipUntil(Observable.empty());

        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);

        m.subscribe(ts);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onNext(any());
        verify(observer, never()).onComplete();
    }

    @Test
    public void otherFiresAndCompletes() {
        PublishSubject<Integer> source = PublishSubject.create();
        PublishSubject<Integer> other = PublishSubject.create();

        Observable<Integer> m = source.skipUntil(other);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);

        m.subscribe(ts);

        source.onNext(0);
        source.onNext(1);

        other.onNext(100);
        other.onComplete();

        source.onNext(2);
        source.onNext(3);
        source.onNext(4);
        source.onComplete();

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onNext(2);
        verify(observer, times(1)).onNext(3);
        verify(observer, times(1)).onNext(4);
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void sourceThrows() {
        PublishSubject<Integer> source = PublishSubject.create();
        PublishSubject<Integer> other = PublishSubject.create();

        Observable<Integer> m = source.skipUntil(other);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);

        m.subscribe(ts);

        source.onNext(0);
        source.onNext(1);

        other.onNext(100);
        other.onComplete();

        source.onNext(2);
        source.onError(new RuntimeException("Forced failure"));

        verify(observer, times(1)).onNext(2);
        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
    }

    @Test
    public void otherThrowsImmediately() {
        PublishSubject<Integer> source = PublishSubject.create();
        PublishSubject<Integer> other = PublishSubject.create();

        Observable<Integer> m = source.skipUntil(other);
        @SuppressWarnings("unchecked")
        Subscriber<Integer> observer = mock(Subscriber.class);
        TestSubscriber<Integer> ts = new TestSubscriber<>(observer);

        m.subscribe(ts);

        source.onNext(0);
        source.onNext(1);

        other.onError(new RuntimeException("Forced failure"));

        verify(observer, never()).onNext(any());
        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
    }
}
