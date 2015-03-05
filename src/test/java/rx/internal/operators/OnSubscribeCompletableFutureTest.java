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

package rx.internal.operators;

import java.util.concurrent.CompletableFuture;

import org.junit.Test;

import rx.Observable;
import rx.disposables.Disposable;
import rx.subscribers.TestSubscriber;

/**
 * 
 */
public class OnSubscribeCompletableFutureTest {
    @Test
    public void simple() {
        CompletableFuture<Integer> f = CompletableFuture.completedFuture(1);
        
        TestSubscriber<Integer> ts = new TestSubscriber<>(0);
        
        Observable.from(f).subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNoComplete();
        
        ts.requestMore(10);
        
        ts.assertValues(1);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    @Test
    public void testDelayed() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Observable.from(f).subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNoComplete();
        
        f.complete(1);

        ts.assertValues(1);
        ts.assertComplete();
        ts.assertNoErrors();
    }
    @Test
    public void testCancelled() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        f.cancel(false);

        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Observable.from(f).subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNoComplete();
    }
    @Test
    public void testDelayedCancelled() {
        CompletableFuture<Integer> f = new CompletableFuture<>();
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Observable.from(f).subscribe(ts);
        
        ts.assertNoValues();
        ts.assertNoErrors();
        ts.assertNoComplete();
        
        f.cancel(false);

        ts.assertNoComplete();
        ts.assertNoErrors();
        ts.assertNoValues();
    }
    @Test
    public void client1Cancels2Normal() {
        CompletableFuture<Integer> f = new CompletableFuture<>();

        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        Disposable d = Observable.from(f).subscribeDisposable(ts1);
        
        ts1.assertNoValues();
        ts1.assertNoErrors();
        ts1.assertNoComplete();
        
        d.dispose();

        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        Observable.from(f).subscribe(ts2);

        f.complete(1);

        ts1.assertNoValues();
        ts1.assertNoErrors();
        ts1.assertNoComplete();

        ts2.assertValues(1);
        ts2.assertComplete();
        ts2.assertNoErrors();

    }
}
