/**
 * Copyright 2015 David Karnok
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.internal.subscriptions;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.exceptions.*;
import rx.subscribers.TestSubscriber;
/**
 *
 */
public class SubscriptionArbiterTest {
    <T> SubscriptionArbiter<T> create(Subscriber<? super T> subscriber) {
        return new SubscriptionArbiter<>(subscriber);
    }
    @Test(expected = NullPointerException.class)
    public void conformanceNonNullItem() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);

        qs.onNext(null);
    }

    @Test(expected = NullPointerException.class)
    public void conformanceNonNullThrowable() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);

        qs.onError(null);
    }
    
    @Test(expected = NullPointerException.class)
    public void conformanceNonNullSubscriptionOnNext() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);

        qs.onNext(1);
    }

    @Test(expected = NullPointerException.class)
    public void conformanceNonNullSubscriptionOnError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);

        qs.onError(new TestException());
    }

    @Test(expected = NullPointerException.class)
    public void conformanceNonNullSubscriptionOnComplete() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);

        qs.onComplete();
    }
    
    @Test
    public void conformancePositiveTry0() {
        SubscriptionConformanceTest.conformancePositiveTry0(this::create);
    }
    @Test
    public void conformancePositiveTryMinus1() {
        SubscriptionConformanceTest.conformancePositiveTryMinus1(this::create);
    }
    @Test
    public void conformanceRequestAfterCancelNoError() {
        SubscriptionConformanceTest.conformanceRequestAfterCancelNoError(this::create);
    }
    @Test(expected = NullPointerException.class)
    public void conformanceSubscriberNonNull() {
        SubscriptionConformanceTest.conformanceSubscriberNonNull(this::create);
    }

    @Test
    public void accumulateRequestsBeforeSet() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        ts.requestMore(1);
        
        assertEquals(1, qs.requested());

        ts.requestMore(2);

        assertEquals(3, qs.requested());
        
        ts.requestMore(Long.MAX_VALUE);
        
        assertEquals(Long.MAX_VALUE, qs.requested());
        
        qs.set(AbstractSubscription.createEmpty(ts));
        
        qs.onNext(1);
        
        ts.assertValues(1);
        ts.assertNoTerminalEvent();
        
        qs.onNext(2);
        qs.onComplete();
        
        ts.assertValues(1, 2);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    
    @Test
    public void errorCutsAhead() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0) {
            @Override
            public void onNext(Integer item) {
                super.onNext(item);
                if (item == 0) {
                    ((SubscriptionArbiter<?>)subscription()).onError(new TestException());
                }
            }
        };
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        qs.set(AbstractSubscription.createEmpty(ts));
        
        ts.requestMore(10);
        
        qs.onNext(0);
        qs.onNext(1);
        
        ts.assertValues(0);
        ts.assertError(TestException.class);
        ts.assertNoComplete();
        
        assertTrue(qs.isDisposed());
    }
    
    @Test
    public void cancelRetainsSubscription() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        Subscription createEmpty = AbstractSubscription.createEmpty(ts);
        qs.set(createEmpty);
        
        qs.cancel();
        
        assertEquals(createEmpty, qs.current);
    }
    
    @Test
    public void cancelCutsAhead() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0) {
            @Override
            public void onNext(Integer item) {
                super.onNext(item);
                if (item == 0) {
                    ((SubscriptionArbiter<?>)subscription()).cancel();
                }
            }
        };
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        qs.set(AbstractSubscription.createEmpty(ts));
        
        ts.requestMore(10);
        
        qs.onNext(0);
        qs.onNext(1);
        
        ts.assertValues(0);
        ts.assertNoErrors();
        ts.assertNoComplete();
        
        assertTrue(qs.isDisposed());
    }
    
    @Test
    public void noEventsBeforeRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        qs.set(AbstractSubscription.createEmpty(ts));

        qs.onNext(1);
        qs.onNext(2);
        qs.onError(new TestException());
        qs.onComplete();
        
        ts.assertValues();
        ts.assertNoComplete();
        ts.assertError(MissingBackpressureException.class);
    }
    
    @Test
    public void noEventsAfterCancel() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        qs.set(AbstractSubscription.createEmpty(ts));

        ts.requestMore(1);
        
        qs.onNext(0);
        qs.cancel();
        
        qs.onNext(1);
        qs.onError(new Throwable());
        qs.onComplete();

        assertEquals(Long.MIN_VALUE, qs.requested());
        assertTrue(qs.isDisposed());

        ts.assertNoErrors();
        ts.assertNoComplete();
        ts.assertValues(0);
        
    }
    
    @Test
    public void cancelIdempotent() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        Subscription createEmpty = mock(Subscription.class);
        
        qs.set(createEmpty);
        
        qs.cancel();
        qs.cancel();

        verify(createEmpty).cancel();
        
        assertEquals(createEmpty, qs.current);
    }
    
    @Test
    public void subscriptionChangeAndComplete() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        Subscription first = mock(Subscription.class);
        InOrder inOrder1 = inOrder(first);
        Subscription second = mock(Subscription.class);
        InOrder inOrder2 = inOrder(second);
        
        qs.set(first);
        
        ts.requestMore(5);
        
        inOrder1.verify(first).request(5);
        
        qs.onNext(1);
        qs.onNext(2);
        
        assertEquals(3, qs.requested());
        
        qs.set(second);
        
        inOrder1.verify(first).cancel();
        
        inOrder2.verify(second).request(3);
        
        ts.requestMore(5);
        
        assertEquals(8, qs.requested());
        inOrder2.verify(second).request(5);
        
        qs.onNext(3);
        qs.onComplete();
        
        qs.onNext(4);
        qs.onError(new TestException());
        qs.onComplete();
        
        inOrder1.verifyNoMoreInteractions();
        inOrder2.verify(second).cancel();
        inOrder2.verifyNoMoreInteractions();
        
        ts.assertNoErrors();
        ts.assertComplete();
        ts.assertValues(1, 2, 3);
    }
    @Test
    public void subscriptionChangeAndError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        Subscription first = mock(Subscription.class);
        InOrder inOrder1 = inOrder(first);
        Subscription second = mock(Subscription.class);
        InOrder inOrder2 = inOrder(second);
        
        qs.set(first);
        
        ts.requestMore(5);
        
        inOrder1.verify(first).request(5);
        
        qs.onNext(1);
        qs.onNext(2);
        
        assertEquals(3, qs.requested());
        
        qs.set(second);
        
        inOrder1.verify(first).cancel();
        
        inOrder2.verify(second).request(3);
        
        ts.requestMore(5);
        
        assertEquals(8, qs.requested());
        inOrder2.verify(second).request(5);
        
        qs.onNext(3);
        qs.onError(new TestException());

        qs.onNext(4);
        qs.onError(new TestException());
        qs.onComplete();
        
        inOrder1.verifyNoMoreInteractions();
        inOrder2.verify(second).cancel();
        inOrder2.verifyNoMoreInteractions();
        
        ts.assertError(TestException.class);
        ts.assertNoComplete();
        ts.assertValues(1, 2, 3);
    }
    
    @Test
    public void setAfterCancel() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        SubscriptionArbiter<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        Subscription first = mock(Subscription.class);

        ts.requestMore(5);
        
        qs.cancel();
        qs.set(first);
        
        verify(first, never()).request(anyLong());
        verify(first).cancel();
    }
}
