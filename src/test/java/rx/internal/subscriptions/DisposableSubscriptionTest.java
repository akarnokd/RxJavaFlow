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

import org.junit.Test;

import rx.Flow.Subscription;
import rx.disposables.Disposable;
import rx.subscribers.TestSubscriber;

/**
 *
 */
public class DisposableSubscriptionTest {
    @Test
    public void addAndDispose() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Subscription s = AbstractSubscription.createEmpty(ts);
        
        CompositeDisposableSubscription ds = new CompositeDisposableSubscription(s);
        
        ts.onSubscribe(ds);
        
        Disposable d = Disposable.empty();
        
        ds.add(d);
        
        ts.cancel();
        
        assertTrue(d.isDisposed());
        assertTrue(ds.isDisposed());
    }
    @Test
    public void disposeAndAdd() {
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Subscription s = AbstractSubscription.createEmpty(ts);
        
        CompositeDisposableSubscription ds = new CompositeDisposableSubscription(s);
        
        ts.onSubscribe(ds);
        ts.cancel();

        assertTrue(ds.isDisposed());

        Disposable d = Disposable.empty();
        ds.add(d);
        
        
        assertTrue(d.isDisposed());
    }
    
    @Test(expected = NullPointerException.class)
    public void conformanceSubscriberNonNull() {
        new CompositeDisposableSubscription(null);
    }

}
