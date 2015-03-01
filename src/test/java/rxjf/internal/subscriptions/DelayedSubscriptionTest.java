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

package rxjf.internal.subscriptions;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.mockito.InOrder;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.subscriptions.DelayedSubscription;

/**
 * 
 */
public class DelayedSubscriptionTest {
    @Test
    public void actualNeverSet() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);
        
        DelayedSubscription ds = new DelayedSubscription(subscriber);
        
        ds.request(1);
        
        assertEquals(1, ds.requested);
        
        ds.request(3);
        
        assertEquals(4, ds.requested);
        
        ds.cancel();
        
        assertEquals(DelayedSubscription.CANCELLED, ds.requested);
    }
    @Test
    public void actualSet() {
        Subscription subscription = mock(Subscription.class);
        InOrder inOrder = inOrder(subscription);
        
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);
        
        DelayedSubscription ds = new DelayedSubscription(subscriber);
        ds.request(1);
        
        assertEquals(1, ds.requested);
        
        ds.request(3);
        
        assertEquals(4, ds.requested);
        
        ds.setActual(subscription);
        
        assertEquals(subscription, ds.actual);
        assertEquals(DelayedSubscription.REPLACED, ds.requested);
        
        inOrder.verify(subscription).request(4);
        
        ds.request(10);
        
        inOrder.verify(subscription).request(10);
        
        ds.cancel();
        
        inOrder.verify(subscription).cancel();

        assertEquals(DelayedSubscription.REPLACED, ds.requested);
        
        verify(subscriber, never()).onNext(any());
        verify(subscriber, never()).onError(any());
        verify(subscriber, never()).onComplete();
    }
    @Test
    public void actualImmediatelySet() {
        Subscription subscription = mock(Subscription.class);
        InOrder inOrder = inOrder(subscription);
        
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);
        
        DelayedSubscription ds = new DelayedSubscription(subscriber);
        ds.setActual(subscription);

        inOrder.verify(subscription, never()).request(anyInt());
        inOrder.verify(subscription, never()).cancel();
        
        assertEquals(subscription, ds.actual);
        assertEquals(DelayedSubscription.REPLACED, ds.requested);

        ds.request(4);

        inOrder.verify(subscription).request(4);
        
        ds.request(10);
        
        inOrder.verify(subscription).request(10);
        
        ds.cancel();
        
        inOrder.verify(subscription).cancel();

        assertEquals(DelayedSubscription.REPLACED, ds.requested);
        
        verify(subscriber, never()).onNext(any());
        verify(subscriber, never()).onError(any());
        verify(subscriber, never()).onComplete();
    }
    @Test
    public void setAfterCancel() {
        Subscription subscription = mock(Subscription.class);
        InOrder inOrder = inOrder(subscription);
        
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);
        
        DelayedSubscription ds = new DelayedSubscription(subscriber);

        ds.request(5);
        ds.cancel();
        
        ds.setActual(subscription);
        
        inOrder.verify(subscription).cancel();
        verify(subscription, never()).request(anyInt());
    }
    
    // -------------------------------
    // Conformance testing
    @Test(expected = NullPointerException.class)
    public void conformanceNonNull() {
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);

        DelayedSubscription ds = new DelayedSubscription(subscriber);
        
        ds.setActual(null);
        
        verify(subscriber, never()).onNext(any());
        verify(subscriber, never()).onError(any());
        verify(subscriber, never()).onComplete();
    }
    @Test
    public void conformancePositiveTry0() {
        SubscriptionConformanceTest.conformancePositiveTry0(DelayedSubscription::new);
    }
    @Test
    public void conformancePositiveTryMinus1() {
        SubscriptionConformanceTest.conformancePositiveTryMinus1(DelayedSubscription::new);
    }
    
    @Test
    public void conformanceRequestAfterCancelNoError() {
        SubscriptionConformanceTest.conformanceRequestAfterCancelNoError(DelayedSubscription::new);
    }
    @Test
    public void conformanceOneSubscription() {
        Subscription subscription = mock(Subscription.class);
        Subscription subscription2 = mock(Subscription.class);
        @SuppressWarnings("unchecked")
        Subscriber<Object> subscriber = mock(Subscriber.class);

        DelayedSubscription ds = new DelayedSubscription(subscriber);

        ds.setActual(subscription);
        
        ds.setActual(subscription2);
        
        verify(subscriber, never()).onNext(any());
        verify(subscriber).onError(any(IllegalArgumentException.class));
        verify(subscriber, never()).onComplete();
    }
    @Test(expected = NullPointerException.class)
    public void conformanceSubscriberNonNull() {
        SubscriptionConformanceTest.conformanceSubscriberNonNull(DelayedSubscription::new);
    }
}
