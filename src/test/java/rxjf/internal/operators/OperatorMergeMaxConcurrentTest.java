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

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import rxjf.Flow.Subscriber;
import rxjf.*;
import rxjf.internal.subscriptions.AbstractSubscription;
import rxjf.schedulers.Schedulers;
import rxjf.subscribers.TestSubscriber;

public class OperatorMergeMaxConcurrentTest {

    @Test
    public void testWhenMaxConcurrentIsOne() {
        for (int i = 0; i < 100; i++) {
            List<Flowable<String>> os = new ArrayList<>();
            os.add(Flowable.just("one", "two", "three", "four", "five").subscribeOn(Schedulers.newThread()));
            os.add(Flowable.just("one", "two", "three", "four", "five").subscribeOn(Schedulers.newThread()));
            os.add(Flowable.just("one", "two", "three", "four", "five").subscribeOn(Schedulers.newThread()));

            List<String> expected = Arrays.asList("one", "two", "three", "four", "five", "one", "two", "three", "four", "five", "one", "two", "three", "four", "five");
            Iterator<String> iter = Flowable.merge(os, 1).toBlocking().toIterable().iterator();
            List<String> actual = new ArrayList<>();
            while (iter.hasNext()) {
                actual.add(iter.next());
            }
            assertEquals(expected, actual);
        }
    }

    @Test
    public void testMaxConcurrent() {
        for (int times = 0; times < 100; times++) {
            int observableCount = 100;
            // Test maxConcurrent from 2 to 12
            int maxConcurrent = 2 + (times % 10);
            AtomicInteger subscriptionCount = new AtomicInteger(0);

            List<Flowable<String>> os = new ArrayList<>();
            List<SubscriptionCheckFlowable> scos = new ArrayList<>();
            for (int i = 0; i < observableCount; i++) {
                SubscriptionCheckFlowable sco = new SubscriptionCheckFlowable(subscriptionCount, maxConcurrent);
                scos.add(sco);
                os.add(Flowable.create(sco));
            }

            Iterator<String> iter = Flowable.merge(os, maxConcurrent).toBlocking().toIterable().iterator();
            List<String> actual = new ArrayList<>();
            while (iter.hasNext()) {
                actual.add(iter.next());
            }
            //            System.out.println("actual: " + actual);
            assertEquals(5 * observableCount, actual.size());
            for (SubscriptionCheckFlowable sco : scos) {
                assertFalse(sco.failed);
            }
        }
    }

    private static class SubscriptionCheckFlowable implements Flowable.OnSubscribe<String> {

        private final AtomicInteger subscriptionCount;
        private final int maxConcurrent;
        volatile boolean failed = false;

        SubscriptionCheckFlowable(AtomicInteger subscriptionCount, int maxConcurrent) {
            this.subscriptionCount = subscriptionCount;
            this.maxConcurrent = maxConcurrent;
        }

        @Override
        public void accept(final Subscriber<? super String> t1) {
            new Thread(new Runnable() {

                @Override
                public void run() {
                    if (subscriptionCount.incrementAndGet() > maxConcurrent) {
                        failed = true;
                    }
                    AbstractSubscription.setEmptyOn(t1);
                    t1.onNext("one");
                    t1.onNext("two");
                    t1.onNext("three");
                    t1.onNext("four");
                    t1.onNext("five");
                    // We could not decrement subscriptionCount in the unsubscribe method
                    // as "unsubscribe" is not guaranteed to be called before the next "subscribe".
                    subscriptionCount.decrementAndGet();
                    t1.onComplete();
                }

            }).start();
        }

    }
    
    @Test
    public void testMergeALotOfSourcesOneByOneSynchronously() {
        int n = 10000;
        List<Flowable<Integer>> sourceList = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sourceList.add(Flowable.just(i));
        }
        Iterator<Integer> it = Flowable.merge(Flowable.from(sourceList), 1).toBlocking().getIterator();
        int j = 0;
        while (it.hasNext()) {
            assertEquals((Integer)j, it.next());
            j++;
        }
        assertEquals(j, n);
    }
    @Test
    public void testMergeALotOfSourcesOneByOneSynchronouslyTakeHalf() {
        int n = 10000;
        List<Flowable<Integer>> sourceList = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sourceList.add(Flowable.just(i));
        }
        Iterator<Integer> it = Flowable.merge(Flowable.from(sourceList), 1).take(n / 2).toBlocking().getIterator();
        int j = 0;
        while (it.hasNext()) {
            assertEquals((Integer)j, it.next());
            j++;
        }
        assertEquals(j, n / 2);
    }
    
    @Test
    public void testSimple() {
        for (int i = 1; i < 100; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Flowable<Integer>> sourceList = new ArrayList<>(i);
            List<Integer> result = new ArrayList<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Flowable.just(j));
                result.add(j);
            }
            
            Flowable.merge(sourceList, i).subscribe(ts);
        
            ts.assertNoErrors();
            ts.assertTerminalEvent();
            ts.assertValues(result);
        }
    }
    @Test
    public void testSimpleOneLess() {
        for (int i = 2; i < 100; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Flowable<Integer>> sourceList = new ArrayList<>(i);
            List<Integer> result = new ArrayList<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Flowable.just(j));
                result.add(j);
            }
            
            Flowable.merge(sourceList, i - 1).subscribe(ts);
        
            ts.assertNoErrors();
            ts.assertTerminalEvent();
            ts.assertValues(result);
        }
    }
    @Test(timeout = 10000)
    public void testSimpleAsyncLoop() {
        for (int i = 0; i < 200; i++) {
            testSimpleAsync();
        }
    }
    @Test//(timeout = 10000)
    public void testSimpleAsync() {
        for (int i = 1; i < 50; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Flowable<Integer>> sourceList = new ArrayList<>(i);
            Set<Integer> expected = new HashSet<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Flowable.just(j).subscribeOn(Schedulers.io()));
                expected.add(j);
            }
            
            Flowable.merge(sourceList, i).subscribe(ts);
        
            ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
            ts.assertNoErrors();
            Set<Integer> actual = new HashSet<>(ts.getValues());
            
            assertEquals(expected, actual);
            System.out.println("testSimpleAsync => " + i + " passed");
        }
    }
    @Test(timeout = 10000)
    public void testSimpleOneLessAsyncLoop() {
        for (int i = 0; i < 200; i++) {
            testSimpleOneLessAsync();
        }
    }
    @Test(timeout = 10000)
    public void testSimpleOneLessAsync() {
        for (int i = 2; i < 50; i++) {
            TestSubscriber<Integer> ts = new TestSubscriber<>();
            List<Flowable<Integer>> sourceList = new ArrayList<>(i);
            Set<Integer> expected = new HashSet<>(i);
            for (int j = 1; j <= i; j++) {
                sourceList.add(Flowable.just(j).subscribeOn(Schedulers.io()));
                expected.add(j);
            }
            
            Flowable.merge(sourceList, i - 1).subscribe(ts);
        
            ts.awaitTerminalEvent(1, TimeUnit.SECONDS);
            ts.assertNoErrors();
            Set<Integer> actual = new HashSet<>(ts.getValues());
            
            assertEquals(expected, actual);
        }
    }
    @Test(timeout = 5000)
    public void testBackpressureHonored() throws Exception {
        List<Flowable<Integer>> sourceList = new ArrayList<>(3);
        
        sourceList.add(Flowable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Flowable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Flowable.range(0, 100000).subscribeOn(Schedulers.io()));
        
        final CountDownLatch cdl = new CountDownLatch(5);
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>(0) {
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                cdl.countDown();
            }
        };
        
        Flowable.merge(sourceList, 2).subscribe(ts);
        
        ts.requestMore(5);
        
        cdl.await();
        
        ts.assertNoErrors();
        ts.assertNoComplete();
        ts.assertValueCount(5);
        
        ts.cancel();
    }
    @Test(timeout = 5000)
    public void testTake() throws Exception {
        List<Flowable<Integer>> sourceList = new ArrayList<>(3);
        
        sourceList.add(Flowable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Flowable.range(0, 100000).subscribeOn(Schedulers.io()));
        sourceList.add(Flowable.range(0, 100000).subscribeOn(Schedulers.io()));
        
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        Flowable.merge(sourceList, 2).take(5).subscribe(ts);
        
        ts.awaitTerminalEvent();
        ts.assertNoErrors();
        ts.assertValueCount(5);
    }
}
