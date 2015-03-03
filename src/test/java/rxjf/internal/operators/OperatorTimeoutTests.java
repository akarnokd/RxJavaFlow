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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.MockitoAnnotations;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.Subscription;
import rx.schedulers.TestScheduler;
import rx.subjects.PublishSubject;

public class OperatorTimeoutTests {
    private PublishSubject<String> underlyingSubject;
    private TestScheduler testScheduler;
    private Flowable<String> withTimeout;
    private static final long TIMEOUT = 3;
    private static final TimeUnit TIME_UNIT = TimeUnit.SECONDS;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        underlyingSubject = PublishSubject.create();
        testScheduler = new TestScheduler();
        withTimeout = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, testScheduler);
    }

    @Test
    public void shouldNotTimeoutIfOnNextWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        verify(observer).onNext("One");
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer, never()).onError(any(Throwable.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldNotTimeoutIfSecondOnNextWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("Two");
        verify(observer).onNext("Two");
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer, never()).onError(any(Throwable.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldTimeoutIfOnNextNotWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(TIMEOUT + 1, TimeUnit.SECONDS);
        verify(observer).onError(any(TimeoutException.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldTimeoutIfSecondOnNextNotWithinTimeout() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        verify(observer).onNext("One");
        testScheduler.advanceTimeBy(TIMEOUT + 1, TimeUnit.SECONDS);
        verify(observer).onError(any(TimeoutException.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldCompleteIfUnderlyingComletes() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onComplete();
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldErrorIfUnderlyingErrors() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = withTimeout.subscribe(observer);
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onError(new UnsupportedOperationException());
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        verify(observer).onError(any(UnsupportedOperationException.class));
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherIfOnNextNotWithinTimeout() {
        Flowable<String> other = Flowable.just("a", "b", "c");
        Flowable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onNext("Two");
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verify(observer, times(1)).onNext("c");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherIfOnErrorNotWithinTimeout() {
        Flowable<String> other = Flowable.just("a", "b", "c");
        Flowable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onError(new UnsupportedOperationException());
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verify(observer, times(1)).onNext("c");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherIfonComplete()NotWithinTimeout() {
        Flowable<String> other = Flowable.just("a", "b", "c");
        Flowable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onComplete();
        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verify(observer, times(1)).onNext("c");
        inOrder.verify(observer, times(1)).onComplete();
        inOrder.verifyNoMoreInteractions();
        subscription.unsubscribe();
    }

    @Test
    public void shouldSwitchToOtherAndCanBeUnsubscribedIfOnNextNotWithinTimeout() {
        PublishSubject<String> other = PublishSubject.create();
        Flowable<String> source = underlyingSubject.timeout(TIMEOUT, TIME_UNIT, other, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        Subscription subscription = source.subscribe(observer);

        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);
        underlyingSubject.onNext("One");
        testScheduler.advanceTimeBy(4, TimeUnit.SECONDS);
        underlyingSubject.onNext("Two");

        other.onNext("a");
        other.onNext("b");
        subscription.unsubscribe();

        // The following messages should not be delivered.
        other.onNext("c");
        other.onNext("d");
        other.onComplete();

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext("One");
        inOrder.verify(observer, times(1)).onNext("a");
        inOrder.verify(observer, times(1)).onNext("b");
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void shouldTimeoutIfSynchronizedFlowableEmitFirstOnNextNotWithinTimeout()
            throws InterruptedException {
        final CountDownLatch exit = new CountDownLatch(1);
        final CountDownLatch timeoutSetuped = new CountDownLatch(1);

        @SuppressWarnings("unchecked")
        final Observer<String> observer = mock(Observer.class);
        new Thread(new Runnable() {

            @Override
            public void run() {
                Flowable.create(new OnSubscribe<String>() {

                    @Override
                    public void call(Subscriber<? super String> subscriber) {
                        try {
                            timeoutSetuped.countDown();
                            exit.await();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        subscriber.onNext("a");
                        subscriber.onComplete();
                    }

                }).timeout(1, TimeUnit.SECONDS, testScheduler)
                        .subscribe(observer);
            }
        }).start();

        timeoutSetuped.await();
        testScheduler.advanceTimeBy(2, TimeUnit.SECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(isA(TimeoutException.class));
        inOrder.verifyNoMoreInteractions();

        exit.countDown(); // exit the thread
    }

    @Test
    public void shouldUnsubscribeFromUnderlyingSubscriptionOnTimeout() throws InterruptedException {
        // From https://github.com/ReactiveX/RxJava/pull/951
        final Subscription s = mock(Subscription.class);

        Flowable<String> never = Flowable.create(new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.add(s);
            }
        });

        TestScheduler testScheduler = new TestScheduler();
        Flowable<String> observableWithTimeout = never.timeout(1000, TimeUnit.MILLISECONDS, testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observableWithTimeout.subscribe(observer);

        testScheduler.advanceTimeBy(2000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).onError(isA(TimeoutException.class));
        inOrder.verifyNoMoreInteractions();

        verify(s, times(1)).unsubscribe();
    }

    @Test
    public void shouldUnsubscribeFromUnderlyingSubscriptionOnImmediatelyComplete() {
        // From https://github.com/ReactiveX/RxJava/pull/951
        final Subscription s = mock(Subscription.class);

        Flowable<String> immediatelyComplete = Flowable.create(new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.add(s);
                subscriber.onComplete();
            }
        });

        TestScheduler testScheduler = new TestScheduler();
        Flowable<String> observableWithTimeout = immediatelyComplete.timeout(1000, TimeUnit.MILLISECONDS,
                testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observableWithTimeout.subscribe(observer);

        testScheduler.advanceTimeBy(2000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).onComplete();
        inOrder.verifyNoMoreInteractions();

        verify(s, times(1)).unsubscribe();
    }

    @Test
    public void shouldUnsubscribeFromUnderlyingSubscriptionOnImmediatelyErrored() throws InterruptedException {
        // From https://github.com/ReactiveX/RxJava/pull/951
        final Subscription s = mock(Subscription.class);

        Flowable<String> immediatelyError = Flowable.create(new OnSubscribe<String>() {
            @Override
            public void call(Subscriber<? super String> subscriber) {
                subscriber.add(s);
                subscriber.onError(new IOException("Error"));
            }
        });

        TestScheduler testScheduler = new TestScheduler();
        Flowable<String> observableWithTimeout = immediatelyError.timeout(1000, TimeUnit.MILLISECONDS,
                testScheduler);

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        observableWithTimeout.subscribe(observer);

        testScheduler.advanceTimeBy(2000, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer).onError(isA(IOException.class));
        inOrder.verifyNoMoreInteractions();

        verify(s, times(1)).unsubscribe();
    }
}
