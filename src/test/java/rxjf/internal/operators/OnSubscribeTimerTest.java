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
package rxjf.internal.operators;


import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.mockito.*;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.cancellables.Cancellable;
import rxjf.exceptions.TestException;
import rxjf.schedulers.TestScheduler;
import rxjf.subscribers.AbstractSubscriber;

public class OnSubscribeTimerTest {
    @Mock
    Subscriber<Object> observer;
    @Mock
    Subscriber<Long> observer2;
    TestScheduler scheduler;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        scheduler = new TestScheduler();
    }

    @Test
    public void testTimerOnce() {
        Flowable.timer(100, TimeUnit.MILLISECONDS, scheduler).subscribe(observer);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);

        verify(observer, times(1)).onNext(0L);
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testTimerPeriodically() {
        Cancellable c = Flowable.timer(100, 100, TimeUnit.MILLISECONDS, scheduler)
                .subscribeCancellable(observer);
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(0L);

        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(1L);

        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(2L);

        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, times(1)).onNext(3L);

        c.cancel();
        scheduler.advanceTimeBy(100, TimeUnit.MILLISECONDS);
        inOrder.verify(observer, never()).onNext(any());

        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }
    @Test
    public void testInterval() {
        Flowable<Long> w = Flowable.interval(1, TimeUnit.SECONDS, scheduler);
        Cancellable sub = w.subscribeCancellable(observer);

        verify(observer, never()).onNext(0L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        scheduler.advanceTimeTo(2, TimeUnit.SECONDS);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(0L);
        inOrder.verify(observer, times(1)).onNext(1L);
        inOrder.verify(observer, never()).onNext(2L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        sub.cancel();
        scheduler.advanceTimeTo(4, TimeUnit.SECONDS);
        verify(observer, never()).onNext(2L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testWithMultipleSubscribersStartingAtSameTime() {
        Flowable<Long> w = Flowable.interval(1, TimeUnit.SECONDS, scheduler);
        Cancellable sub1 = w.subscribeCancellable(observer);
        Cancellable sub2 = w.subscribeCancellable(observer2);

        verify(observer, never()).onNext(anyLong());
        verify(observer2, never()).onNext(anyLong());

        scheduler.advanceTimeTo(2, TimeUnit.SECONDS);

        InOrder inOrder1 = inOrder(observer);
        InOrder inOrder2 = inOrder(observer2);

        inOrder1.verify(observer, times(1)).onNext(0L);
        inOrder1.verify(observer, times(1)).onNext(1L);
        inOrder1.verify(observer, never()).onNext(2L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        inOrder2.verify(observer2, times(1)).onNext(0L);
        inOrder2.verify(observer2, times(1)).onNext(1L);
        inOrder2.verify(observer2, never()).onNext(2L);
        verify(observer2, never()).onComplete();
        verify(observer2, never()).onError(any(Throwable.class));

        sub1.cancel();
        sub2.cancel();
        scheduler.advanceTimeTo(4, TimeUnit.SECONDS);

        verify(observer, never()).onNext(2L);
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        verify(observer2, never()).onNext(2L);
        verify(observer2, never()).onComplete();
        verify(observer2, never()).onError(any(Throwable.class));
    }

    @Test
    public void testWithMultipleStaggeredSubscribers() {
        Flowable<Long> w = Flowable.interval(1, TimeUnit.SECONDS, scheduler);
        Cancellable sub1 = w.subscribeCancellable(observer);

        verify(observer, never()).onNext(anyLong());

        scheduler.advanceTimeTo(2, TimeUnit.SECONDS);
        Cancellable sub2 = w.subscribeCancellable(observer2);

        InOrder inOrder1 = inOrder(observer);
        inOrder1.verify(observer, times(1)).onNext(0L);
        inOrder1.verify(observer, times(1)).onNext(1L);
        inOrder1.verify(observer, never()).onNext(2L);

        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer2, never()).onNext(anyLong());

        scheduler.advanceTimeTo(4, TimeUnit.SECONDS);

        inOrder1.verify(observer, times(1)).onNext(2L);
        inOrder1.verify(observer, times(1)).onNext(3L);

        InOrder inOrder2 = inOrder(observer2);
        inOrder2.verify(observer2, times(1)).onNext(0L);
        inOrder2.verify(observer2, times(1)).onNext(1L);

        sub1.cancel();
        sub2.cancel();

        inOrder1.verify(observer, never()).onNext(anyLong());
        inOrder1.verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        inOrder2.verify(observer2, never()).onNext(anyLong());
        inOrder2.verify(observer2, never()).onComplete();
        verify(observer2, never()).onError(any(Throwable.class));
    }

    @Test
    public void testWithMultipleStaggeredSubscribersAndPublish() {
        ConnectableFlowable<Long> w = Flowable.interval(1, TimeUnit.SECONDS, scheduler)
                .publish();
        Cancellable sub1 = w.subscribeCancellable(observer);
        w.connect();

        verify(observer, never()).onNext(anyLong());

        scheduler.advanceTimeTo(2, TimeUnit.SECONDS);
        Cancellable sub2 = w.subscribeCancellable(observer2);

        InOrder inOrder1 = inOrder(observer);
        inOrder1.verify(observer, times(1)).onNext(0L);
        inOrder1.verify(observer, times(1)).onNext(1L);
        inOrder1.verify(observer, never()).onNext(2L);

        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer2, never()).onNext(anyLong());

        scheduler.advanceTimeTo(4, TimeUnit.SECONDS);

        inOrder1.verify(observer, times(1)).onNext(2L);
        inOrder1.verify(observer, times(1)).onNext(3L);

        InOrder inOrder2 = inOrder(observer2);
        inOrder2.verify(observer2, times(1)).onNext(2L);
        inOrder2.verify(observer2, times(1)).onNext(3L);

        sub1.cancel();
        sub2.cancel();

        inOrder1.verify(observer, never()).onNext(anyLong());
        inOrder1.verify(observer, never()).onComplete();
        verify(observer, never()).onError(any(Throwable.class));

        inOrder2.verify(observer2, never()).onNext(anyLong());
        inOrder2.verify(observer2, never()).onComplete();
        verify(observer2, never()).onError(any(Throwable.class));
    }
    @Test
    public void testOnceObserverThrows() {
        Flowable<Long> source = Flowable.timer(100, TimeUnit.MILLISECONDS, scheduler);
        
        source.subscribe(new AbstractSubscriber<Long>() {

            @Override
            public void onNext(Long t) {
                throw new TestException();
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        });
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        
        verify(observer).onError(any(TestException.class));
        verify(observer, never()).onNext(anyLong());
        verify(observer, never()).onComplete();
    }
    @Test
    public void testPeriodicObserverThrows() {
        Flowable<Long> source = Flowable.timer(100, 100, TimeUnit.MILLISECONDS, scheduler);
        
        InOrder inOrder = inOrder(observer);
        
        source.subscribe(new AbstractSubscriber<Long>() {

            @Override
            public void onNext(Long t) {
                if (t > 0) {
                    throw new TestException();
                }
                observer.onNext(t);
            }

            @Override
            public void onError(Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        });
        
        scheduler.advanceTimeBy(1, TimeUnit.SECONDS);
        
        inOrder.verify(observer).onNext(0L);
        inOrder.verify(observer).onError(any(TestException.class));
        inOrder.verifyNoMoreInteractions();
        verify(observer, never()).onComplete();
    }
}