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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Observer;
import rx.Subscriber;
import rx.exceptions.MissingBackpressureException;
import rx.exceptions.TestException;
import rx.internal.util.RxRingBuffer;
import rx.observers.TestObserver;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

/**
 * Test the onBackpressureBlock() behavior.
 */
public class OnBackpressureBlockTest {
    static final int WAIT = 200;
    
    @Test(timeout = 1000)
    public void testSimpleBelowCapacity() {
        Flowable<Integer> source = Flowable.just(1).onBackpressureBlock(10);
        
        TestObserver<Integer> o = new TestObserver<Integer>();
        source.subscribe(o);
        
        o.assertValues((1));
        o.assertTerminalEvent();
        assertTrue(o.getOnErrorEvents().isEmpty());
    }
    @Test(timeout = 10000)
    public void testSimpleAboveCapacity() throws InterruptedException {
        Flowable<Integer> source = Flowable.range(1, 11).subscribeOn(Schedulers.newThread())
                .onBackpressureBlock(10);
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
        };
        source.subscribe(o);
        o.requestMore(10);

        Thread.sleep(WAIT);
        
        o.assertValues((1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        
        o.requestMore(10);
        
        Thread.sleep(WAIT);

        o.assertValues((1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));

        o.assertTerminalEvent();
        assertTrue(o.getOnErrorEvents().isEmpty());
    }
    
    @Test(timeout = 3000)
    public void testNoMissingBackpressureException() {
        final int NUM_VALUES = Flow.defaultBufferSize() * 3;
        Flowable<Integer> source = Flowable.create(new OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> t1) {
                for (int i = 0; i < NUM_VALUES; i++) {
                    t1.onNext(i);
                }
                t1.onComplete();
            }
        }).subscribeOn(Schedulers.newThread());
        
        @SuppressWarnings("unchecked")
        Observer<Integer> o = mock(Observer.class);

        TestSubscriber<Integer> s = new TestSubscriber<Integer>(o);
        
        source.onBackpressureBlock(Flow.defaultBufferSize()).observeOn(Schedulers.newThread()).subscribe(s);
        
        s.awaitTerminalEvent();
        
        verify(o, never()).onError(any(MissingBackpressureException.class));
        
        s.assertNoErrors();
        verify(o, times(NUM_VALUES)).onNext(any(Integer.class));
        verify(o).onComplete();
    }
    @Test(timeout = 10000)
    public void testBlockedProducerCanBeUnsubscribed() throws InterruptedException {
        Flowable<Integer> source = Flowable.range(1, 11).subscribeOn(Schedulers.newThread())
                .onBackpressureBlock(5);
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
        };
        source.subscribe(o);
        
        o.requestMore(5);
        
        Thread.sleep(WAIT);
        
        o.unsubscribe();

        Thread.sleep(WAIT);

        o.assertValues((1, 2, 3, 4, 5));
        o.assertNoErrors();
        assertTrue(o.getonComplete()Events().isEmpty());
    }
    @Test(timeout = 10000)
    public void testExceptionIsDelivered() throws InterruptedException {
        Flowable<Integer> source = Flowable.range(1, 10)
                .concatWith(Flowable.<Integer>error(new TestException("Forced failure")))
                .subscribeOn(Schedulers.newThread())
                .onBackpressureBlock(5);
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
        };
        source.subscribe(o);

        o.requestMore(7);

        Thread.sleep(WAIT);
        
        o.assertValues((1, 2, 3, 4, 5, 6, 7));
        o.assertNoErrors();
        assertTrue(o.getonComplete()Events().isEmpty());

        o.requestMore(3);
        
        Thread.sleep(WAIT);

        o.assertValues((1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        o.assertTerminalEvent();
        assertEquals(1, o.getOnErrorEvents().size());
        assertTrue(o.getOnErrorEvents().get(0) instanceof TestException);

        o.requestMore(10);
        
        Thread.sleep(WAIT);
        
        o.assertValues((1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        o.assertTerminalEvent();
        assertEquals(1, o.getOnErrorEvents().size());
        assertTrue(o.getOnErrorEvents().get(0) instanceof TestException);
    }
    @Test(timeout = 10000)
    public void testExceptionIsDeliveredAfterValues() throws InterruptedException {
        Flowable<Integer> source = Flowable.range(1, 10)
                .concatWith(Flowable.<Integer>error(new TestException("Forced failure")))
                .subscribeOn(Schedulers.newThread())
                .onBackpressureBlock(5);
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
        };
        source.subscribe(o);

        o.requestMore(7);

        Thread.sleep(WAIT);
        
        o.assertValues((1, 2, 3, 4, 5, 6, 7));
        o.assertNoErrors();
        assertTrue(o.getonComplete()Events().isEmpty());

        o.requestMore(7);
        
        Thread.sleep(WAIT);

        o.assertValues((1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        assertEquals(1, o.getOnErrorEvents().size());
        assertTrue(o.getOnErrorEvents().get(0) instanceof TestException);
        assertTrue(o.getonComplete()Events().isEmpty());
    }
    @Test(timeout = 10000)
    public void testTakeWorksWithSubscriberRequesting() {
        Flowable<Integer> source = Flowable.range(1, 10)
                .concatWith(Flowable.<Integer>error(new TestException("Forced failure")))
                .subscribeOn(Schedulers.newThread())
                .onBackpressureBlock(5).take(7);
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
        };
        source.subscribe(o);

        o.requestMore(7);

        o.awaitTerminalEvent();
        
        o.assertValues((1, 2, 3, 4, 5, 6, 7));
        o.assertNoErrors();
        o.assertTerminalEvent();
    }
    @Test(timeout = 10000)
    public void testTakeWorksSubscriberRequestUnlimited() {
        Flowable<Integer> source = Flowable.range(1, 10)
                .concatWith(Flowable.<Integer>error(new TestException("Forced failure")))
                .subscribeOn(Schedulers.newThread())
                .onBackpressureBlock(5).take(7);
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>();
        source.subscribe(o);

        o.awaitTerminalEvent();
        
        o.assertValues((1, 2, 3, 4, 5, 6, 7));
        o.assertNoErrors();
        o.assertTerminalEvent();
    }
    @Test(timeout = 10000)
    public void testTakeWorksSubscriberRequestUnlimitedBufferedException() {
        Flowable<Integer> source = Flowable.range(1, 10)
                .concatWith(Flowable.<Integer>error(new TestException("Forced failure")))
                .subscribeOn(Schedulers.newThread())
                .onBackpressureBlock(11).take(7);
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>();
        source.subscribe(o);

        o.awaitTerminalEvent();
        
        o.assertValues((1, 2, 3, 4, 5, 6, 7));
        o.assertNoErrors();
        o.assertTerminalEvent();
    }
    @Test(timeout = 10000)
    public void testonComplete()DoesntWaitIfNoEvents() {
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
        };
        Flowable.<Integer>empty().onBackpressureBlock(2).subscribe(o);
        
        o.assertNoErrors();
        o.assertTerminalEvent();
        o.assertReceivedOnNext(Collections.<Integer>emptyList());
    }
    @Test(timeout = 10000)
    public void testonComplete()DoesWaitIfEvents() {
        
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
        };
        Flowable.just(1).onBackpressureBlock(2).subscribe(o);
        
        o.assertReceivedOnNext(Collections.<Integer>emptyList());
        assertTrue(o.getOnErrorEvents().isEmpty());
        assertTrue(o.getonComplete()Events().isEmpty());
    }
    @Test(timeout = 10000)
    public void testonComplete()DoesntWaitIfNoEvents2() {
        final PublishSubject<Integer> ps = PublishSubject.create();
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                ps.onComplete(); // as if an async completion arrived while in the loop
            }
        };
        ps.onBackpressureBlock(2).unsafeSubscribe(o);
        ps.onNext(1);
        o.requestMore(1);
        
        o.assertNoErrors();
        o.assertTerminalEvent();
        o.assertValues((1));
    }
    @Test(timeout = 10000)
    public void testonComplete()DoesntWaitIfNoEvents3() {
        final PublishSubject<Integer> ps = PublishSubject.create();
        TestSubscriber<Integer> o = new TestSubscriber<Integer>() {
            boolean once = true;
            @Override
            public void onStart() {
                request(0); // make sure it doesn't start in unlimited mode
            }
            @Override
            public void onNext(Integer t) {
                super.onNext(t);
                if (once) {
                    once = false;
                    ps.onNext(2);
                    ps.onComplete(); // as if an async completion arrived while in the loop
                    requestMore(1);
                }
            }
        };
        ps.onBackpressureBlock(3).unsafeSubscribe(o);
        ps.onNext(1);
        o.requestMore(1);
        
        o.assertNoErrors();
        o.assertTerminalEvent();
        o.assertValues((1, 2));
    }
}
