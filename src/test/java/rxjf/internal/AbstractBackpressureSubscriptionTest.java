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

package rxjf.internal;

import static org.junit.Assert.*;

import org.junit.Test;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.exceptions.*;
import rxjf.subscribers.TestSubscriber;

/**
 * 
 */
public abstract class AbstractBackpressureSubscriptionTest {
    
    protected abstract <T> AbstractBackpressureSubscription<T> create(Subscriber<? super T> subscriber);
    
    @Test
    public void unbounded() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        AbstractBackpressureSubscription<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        assertEquals(Long.MAX_VALUE, qs.requested());
        
        qs.onNext(1);
        ts.assertValues(1);

        qs.onNext(2);
        ts.assertValues(1, 2);
        
        qs.onComplete();
        ts.assertValues(1, 2);
        
        qs.onNext(3);
        ts.assertValues(1, 2);
        
        ts.assertNoErrors();
        ts.assertComplete();
        
        assertTrue(qs.isDisposed());
    }
    @Test
    public void bounded() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        AbstractBackpressureSubscription<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        assertEquals(0, qs.requested());
        
        qs.onNext(1);
        ts.assertNoValues();

        qs.onNext(2);
        ts.assertNoValues();

        ts.requestMore(1);
        
        ts.assertValues(1);

        ts.requestMore(3);

        ts.assertValues(1, 2);
        
        qs.onComplete();
        ts.assertValues(1, 2);

        qs.onNext(3);
        ts.assertValues(1, 2);

        ts.assertNoErrors();
        ts.assertComplete();
        
        assertTrue(qs.isDisposed());
    }
    
    @Test
    public void completeWithoutRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        AbstractBackpressureSubscription<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        
        assertEquals(0, qs.requested());

        qs.onComplete();
        
        ts.assertNoErrors();
        ts.assertNoValues();
        ts.assertComplete();
    }
    @Test
    public void errorDelivery() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        AbstractBackpressureSubscription<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        assertEquals(0, qs.requested());

        qs.onNext(1);
        qs.onError(new TestException());

        if (QueueBackpressureSubscription.ERROR_CUTS_AHEAD) {
            ts.assertNoValues();
            ts.assertError(TestException.class);
            ts.assertNoComplete();
        } else {
            ts.requestMore(10);
            ts.assertValues(1);
            ts.assertError(TestException.class);
            ts.assertNoComplete();
        }
        
        assertTrue(qs.isDisposed());
    }

    @Test
    public void chechMissingBackpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        AbstractBackpressureSubscription<Integer> qs = create(ts);
        
        ts.onSubscribe(qs);
        assertEquals(0, qs.requested());
        
        int n = Flow.defaultBufferSize();
        for (int i = 0; i < n + 1; i++) {
            qs.onNext(i);
        }
        
        if (!AbstractBackpressureSubscription.ERROR_CUTS_AHEAD) {
            ts.requestMore(n);
            ts.assertValueCount(n);
            ts.assertLast(n - 1);
        } else {
            ts.assertNoValues();
        }
        ts.assertNoComplete();
        ts.assertError(MissingBackpressureException.class);
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

}
