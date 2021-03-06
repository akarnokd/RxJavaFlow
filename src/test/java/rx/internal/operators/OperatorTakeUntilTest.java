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

import static org.mockito.Mockito.*;

import org.junit.Test;

import rx.Flow.Subscriber;
import rx.*;
import rx.disposables.Disposable;
import rx.internal.subscriptions.SingleDisposableSubscription;
import rx.subscribers.TestSubscriber;

public class OperatorTakeUntilTest {

    @Test
    @SuppressWarnings("unchecked")
    public void testTakeUntil() {
        Disposable sSource = mock(Disposable.class);
        Disposable sOther = mock(Disposable.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);

        Subscriber<String> result = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(result);
        
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        
        stringObservable.subscribe(ts);
        
        source.sendOnNext("one");
        source.sendOnNext("two");
        other.sendOnNext("three");
        source.sendOnNext("four");
        source.sendOnComplete();
        other.sendOnComplete();

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(0)).onNext("four");
        verify(sSource, times(1)).dispose();
        verify(sOther, times(1)).dispose();

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTakeUntilSourceCompleted() {
        Disposable sSource = mock(Disposable.class);
        Disposable sOther = mock(Disposable.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);

        Subscriber<String> result = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(result);
        
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        
        stringObservable.subscribe(ts);
        
        source.sendOnNext("one");
        source.sendOnNext("two");
        source.sendOnComplete();

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(sSource, times(1)).dispose();
        verify(sOther, times(1)).dispose();

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTakeUntilSourceError() {
        Disposable sSource = mock(Disposable.class);
        Disposable sOther = mock(Disposable.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);
        Throwable error = new Throwable();

        Subscriber<String> result = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(result);
        
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        
        stringObservable.subscribe(ts);
        
        source.sendOnNext("one");
        source.sendOnNext("two");
        source.sendOnError(error);
        source.sendOnNext("three");

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(1)).onError(error);
        verify(sSource, times(1)).dispose();
        verify(sOther, times(1)).dispose();

    }

    @Test
    @SuppressWarnings("unchecked")
    public void testTakeUntilOtherError() {
        Disposable sSource = mock(Disposable.class);
        Disposable sOther = mock(Disposable.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);
        Throwable error = new Throwable();

        Subscriber<String> result = mock(Subscriber.class);
        TestSubscriber<String> ts = new TestSubscriber<>(result);
        
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        
        stringObservable.subscribe(ts);
        
        source.sendOnNext("one");
        source.sendOnNext("two");
        other.sendOnError(error);
        source.sendOnNext("three");

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(1)).onError(error);
        verify(result, times(0)).onComplete();
        verify(sSource, times(1)).dispose();
        verify(sOther, times(1)).dispose();

    }

    /**
     * If the 'other' onCompletes then we unsubscribe from the source and onComplete
     */
    @Test
    @SuppressWarnings("unchecked")
    public void testTakeUntilOtherCompleted() {
        Disposable sSource = mock(Disposable.class);
        Disposable sOther = mock(Disposable.class);
        TestObservable source = new TestObservable(sSource);
        TestObservable other = new TestObservable(sOther);

        Subscriber<String> result = mock(Subscriber.class);
        Observable<String> stringObservable = Observable.create(source).takeUntil(Observable.create(other));
        stringObservable.subscribe(result);
        source.sendOnNext("one");
        source.sendOnNext("two");
        other.sendOnComplete();
        source.sendOnNext("three");

        verify(result, times(1)).onNext("one");
        verify(result, times(1)).onNext("two");
        verify(result, times(0)).onNext("three");
        verify(result, times(1)).onComplete();
        verify(sSource, times(1)).dispose();
        verify(sOther, times(1)).dispose(); // unsubscribed since SafeSubscriber unsubscribes after onComplete

    }

    static final class TestObservable implements Observable.OnSubscribe<String> {

        Subscriber<? super String> observer;
        final Disposable s;

        public TestObservable(Disposable s) {
            this.s = s;
        }

        /* used to simulate subscription */
        public void sendOnComplete() {
            observer.onComplete();
        }

        /* used to simulate subscription */
        public void sendOnNext(String value) {
            observer.onNext(value);
        }

        /* used to simulate subscription */
        public void sendOnError(Throwable e) {
            observer.onError(e);
        }

        @Override
        public void accept(Subscriber<? super String> observer) {
            this.observer = observer;
            SingleDisposableSubscription sds = SingleDisposableSubscription.createEmpty(observer);
            sds.set(s);
            observer.onSubscribe(sds);
        }
    }
}
