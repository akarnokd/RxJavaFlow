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

import static org.junit.Assert.assertEquals;

import java.util.function.Function;

import org.junit.Test;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.exceptions.TestException;
import rx.internal.subscriptions.ScalarBackpressureSubscription;
import rx.subscribers.TestSubscriber;

/**
 * 
 */
public class ScalarBackpressureSubscriptionTest {
    @Test
    public void conformancePositiveTry0() {
        Function<Subscriber<?>, Subscription> supplier = s -> new ScalarBackpressureSubscription<>(s);
        // ScalarBackpressureSubscription::new causes raw type warning, either an Eclipse 4.4.2 bug or general type inference shortcoming
        SubscriptionConformanceTest.conformancePositiveTry0(supplier);
    }
    @Test
    public void conformancePositiveTryMinus1() {
        Function<Subscriber<?>, Subscription> supplier = s -> new ScalarBackpressureSubscription<>(s);
        SubscriptionConformanceTest.conformancePositiveTryMinus1(supplier);
    }
    @Test
    public void conformanceRequestAfterCancelNoError() {
        Function<Subscriber<?>, Subscription> supplier = s -> new ScalarBackpressureSubscription<>(s);
        SubscriptionConformanceTest.conformanceRequestAfterCancelNoError(supplier);
    }
    @Test(expected = NullPointerException.class)
    public void conformanceSubscriberNonNull() {
        Function<Subscriber<?>, Subscription> supplier = s -> new ScalarBackpressureSubscription<>(s);
        SubscriptionConformanceTest.conformanceSubscriberNonNull(supplier);
    }

    @Test(expected = NullPointerException.class)
    public void conformanceNonNullItem() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> qs = new ScalarBackpressureSubscription<>(ts);

        qs.onNext(null);
    }

    @Test(expected = NullPointerException.class)
    public void conformanceNonNullThrowable() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> qs = new ScalarBackpressureSubscription<>(ts);

        qs.onError(null);
    }

    @Test
    public void multipleValues() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        ts.onSubscribe(sbs);

        sbs.onNext(1);
        sbs.onNext(2);
        
        ts.assertNoValues();
        ts.assertError(IllegalArgumentException.class);
        ts.assertNoComplete();

        ts.assertSubscription();
    }
    
    @Test
    public void valueBeforeRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        assertEquals(null, sbs.value);
        
        ts.onSubscribe(sbs);

        assertEquals(null, sbs.value);

        sbs.onNext(1);

        assertEquals(1, sbs.value);

        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNoComplete();
        
        ts.requestMore(1);
        
        ts.assertValues(1);
        ts.assertComplete();
        ts.assertNoErrors();
        
        ts.requestMore(1);
        // no more values should be available
        ts.assertValues(1);
        ts.assertComplete();
        ts.assertNoErrors();
        
        sbs.onNext(2);

        // no more values should be accepted
        ts.assertValues(1);
        ts.assertComplete();
        ts.assertNoErrors();

        ts.requestMore(1);

        ts.assertValues(1);
        ts.assertComplete();
        ts.assertNoErrors();
        
        assertEquals(ScalarBackpressureSubscription.TERMINATED, sbs.value);
        
        ts.assertSubscription();
    }
    
    @Test
    public void requestBeforeValue() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(1);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        assertEquals(null, sbs.value);
        
        ts.onSubscribe(sbs);
        
        assertEquals(ScalarBackpressureSubscription.REQUESTED, sbs.value);
        
        ts.requestMore(1);
        
        sbs.onNext(1);

        ts.assertValues(1);
        ts.assertComplete();
        ts.assertNoErrors();
        
        assertEquals(ScalarBackpressureSubscription.TERMINATED, sbs.value);
        
        ts.assertSubscription();
    }
    
    @Test
    public void emptyBeforeRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        ts.onSubscribe(sbs);
        
        sbs.onComplete();
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();
        
        sbs.onNext(1);

        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();

        assertEquals(ScalarBackpressureSubscription.TERMINATED, sbs.value);
        
        ts.assertSubscription();
    }
    @Test
    public void requestBeforeEmpty() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(1);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        ts.onSubscribe(sbs);

        assertEquals(ScalarBackpressureSubscription.REQUESTED, sbs.value);

        
        sbs.onComplete();
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();
        
        sbs.onNext(1);

        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertComplete();

        assertEquals(ScalarBackpressureSubscription.TERMINATED, sbs.value);
        
        ts.assertSubscription();
    }
    @Test
    public void errorBeforeRequest() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        ts.onSubscribe(sbs);
        
        sbs.onError(new TestException());
        
        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNoComplete();
        
        sbs.onNext(1);

        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNoComplete();

        assertEquals(ScalarBackpressureSubscription.TERMINATED, sbs.value);
        
        ts.assertSubscription();
    }
    @Test
    public void requestBeforeError() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(1);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        ts.onSubscribe(sbs);

        assertEquals(ScalarBackpressureSubscription.REQUESTED, sbs.value);

        sbs.onError(new TestException());
        
        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNoComplete();
        
        sbs.onNext(1);

        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNoComplete();

        assertEquals(ScalarBackpressureSubscription.TERMINATED, sbs.value);
        
        ts.assertSubscription();
    }
    
    @Test
    public void errorCutsAhead() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        ts.onSubscribe(sbs);
        
        sbs.onNext(1);
        sbs.onError(new TestException());
        
        ts.assertNoValues();
        ts.assertError(TestException.class);
        ts.assertNoComplete();
    }
    
    @Test
    public void onComplete()oesNotOverwriteValue() {
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        ScalarBackpressureSubscription<Integer> sbs = new ScalarBackpressureSubscription<>(ts);
        
        ts.onSubscribe(sbs);
        
        sbs.onNext(1);
        sbs.onComplete();
        
        ts.requestMore(1);
        
        ts.assertValues(1);
        ts.assertNoErrors();
        ts.assertComplete();
        
    }
}
