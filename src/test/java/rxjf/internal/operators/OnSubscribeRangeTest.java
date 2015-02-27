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

package rxjf.internal.operators;

import org.junit.Test;

import rxjf.Flow.Subscription;
import rxjf.Flowable;
import rxjf.subscribers.TestSubscriber;

/**
 * 
 */
public class OnSubscribeRangeTest {
    @Test
    public void simple() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Flowable<Integer> source = Flowable.range(1, 5);
        
        source.subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoTerminalEvent();
        
        ts.request(2);
        
        ts.assertValues(1, 2);
        ts.assertNoTerminalEvent();
        
        ts.request(6);
        
        ts.assertValues(1, 2, 3, 4, 5);
        
        ts.assertNoError();
        ts.assertComplete();
    }
    @Test
    public void empty() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Flowable<Integer> source = Flowable.range(1, 0);
        
        source.subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoError();
        ts.assertComplete();
    }
    @Test
    public void unbounded() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        Flowable<Integer> source = Flowable.range(1, 5);
        
        source.subscribe(ts);
        
        ts.assertValues(1, 2, 3, 4, 5);
        ts.assertNoError();
        ts.assertComplete();
    }
    
    @Test
    public void takeSome() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                subscription.request(Long.MAX_VALUE);
            }
        };
        Flowable<Integer> source = Flowable.range(1, 5).take(2);
        
        source.subscribe(ts);
        
        ts.assertValues(1, 2);
        ts.assertNoError();
        ts.assertComplete();
    }
}
