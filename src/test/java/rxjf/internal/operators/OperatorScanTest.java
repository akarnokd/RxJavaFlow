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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import rx.Flowable;
import rx.Observer;
import rx.Producer;
import rx.Subscriber;
import rx.functions.Action2;
import rx.functions.Supplier;
import rx.functions.Function;
import rx.functions.BiFunction;
import rx.observers.TestSubscriber;

public class OperatorScanTest {

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testScanIntegersWithInitialValue() {
        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);

        Flowable<Integer> observable = Flowable.just(1, 2, 3);

        Flowable<String> m = observable.scan("", new BiFunction<String, Integer, String>() {

            @Override
            public String call(String s, Integer n) {
                return s + n.toString();
            }

        });
        m.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onNext("");
        verify(observer, times(1)).onNext("1");
        verify(observer, times(1)).onNext("12");
        verify(observer, times(1)).onNext("123");
        verify(observer, times(4)).onNext(anyString());
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testScanIntegersWithoutInitialValue() {
        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);

        Flowable<Integer> observable = Flowable.just(1, 2, 3);

        Flowable<Integer> m = observable.scan(new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2;
            }

        });
        m.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onNext(0);
        verify(observer, times(1)).onNext(1);
        verify(observer, times(1)).onNext(3);
        verify(observer, times(1)).onNext(6);
        verify(observer, times(3)).onNext(anyInt());
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void testScanIntegersWithoutInitialValueAndOnlyOneValue() {
        @SuppressWarnings("unchecked")
        Observer<Integer> observer = mock(Observer.class);

        Flowable<Integer> observable = Flowable.just(1);

        Flowable<Integer> m = observable.scan(new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2;
            }

        });
        m.subscribe(observer);

        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, never()).onNext(0);
        verify(observer, times(1)).onNext(1);
        verify(observer, times(1)).onNext(anyInt());
        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }
    
    @Test
    public void shouldNotEmitUntilAfterSubscription() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        Flowable.range(1, 100).scan(0, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2;
            }

        }).filter(new Function<Integer, Boolean>() {

            @Override
            public Boolean call(Integer t1) {
                // this will cause request(1) when 0 is emitted
                return t1 > 0;
            }
            
        }).subscribe(ts);
        
        assertEquals(100, ts.getOnNextEvents().size());
    }
    
    @Test
    public void testBackpressureWithInitialValue() {
        final AtomicInteger count = new AtomicInteger();
        Flowable.range(1, 100)
                .scan(0, new BiFunction<Integer, Integer, Integer>() {

                    @Override
                    public Integer call(Integer t1, Integer t2) {
                        return t1 + t2;
                    }

                })
                .subscribe(new Subscriber<Integer>() {

                    @Override
                    public void onStart() {
                        request(10);
                    }

                    @Override
                    public void onComplete() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        fail(e.getMessage());
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Integer t) {
                        count.incrementAndGet();
                    }

                });

        // we only expect to receive 10 since we request(10)
        assertEquals(10, count.get());
    }
    
    @Test
    public void testBackpressureWithoutInitialValue() {
        final AtomicInteger count = new AtomicInteger();
        Flowable.range(1, 100)
                .scan(new BiFunction<Integer, Integer, Integer>() {

                    @Override
                    public Integer call(Integer t1, Integer t2) {
                        return t1 + t2;
                    }

                })
                .subscribe(new Subscriber<Integer>() {

                    @Override
                    public void onStart() {
                        request(10);
                    }

                    @Override
                    public void onComplete() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        fail(e.getMessage());
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Integer t) {
                        count.incrementAndGet();
                    }

                });

        // we only expect to receive 10 since we request(10)
        assertEquals(10, count.get());
    }
    
    @Test
    public void testNoBackpressureWithInitialValue() {
        final AtomicInteger count = new AtomicInteger();
        Flowable.range(1, 100)
                .scan(0, new BiFunction<Integer, Integer, Integer>() {

                    @Override
                    public Integer call(Integer t1, Integer t2) {
                        return t1 + t2;
                    }

                })
                .subscribe(new Subscriber<Integer>() {

                    @Override
                    public void onComplete() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        fail(e.getMessage());
                        e.printStackTrace();
                    }

                    @Override
                    public void onNext(Integer t) {
                        count.incrementAndGet();
                    }

                });

        // we only expect to receive 101 as we'll receive all 100 + the initial value
        assertEquals(101, count.get());
    }

    /**
     * This uses the public API collect which uses scan under the covers.
     */
    @Test
    public void testSeedFactory() {
        Flowable<List<Integer>> o = Flowable.range(1, 10)
                .collect(new Supplier<List<Integer>>() {

                    @Override
                    public List<Integer> call() {
                        return new ArrayList<Integer>();
                    }
                    
                }, new Action2<List<Integer>, Integer>() {

                    @Override
                    public void call(List<Integer> list, Integer t2) {
                        list.add(t2);
                    }

                }).takeLast(1);

        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), o.toBlocking().single());
        assertEquals(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), o.toBlocking().single());
    }

    @Test
    public void testScanWithRequestOne() {
        Flowable<Integer> o = Flowable.just(1, 2).scan(0, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2;
            }

        }).take(1);
        TestSubscriber<Integer> subscriber = new TestSubscriber<Integer>();
        o.subscribe(subscriber);
        subscriber.assertReceivedOnNext(Arrays.asList(0));
        subscriber.assertTerminalEvent();
        subscriber.assertNoErrors();
    }

    @Test
    public void testScanShouldNotRequestZero() {
        final AtomicReference<Producer> producer = new AtomicReference<Producer>();
        Flowable<Integer> o = Flowable.create(new Flowable.OnSubscribe<Integer>() {
            @Override
            public void call(final Subscriber subscriber) {
                Producer p = spy(new Producer() {

                    private AtomicBoolean requested = new AtomicBoolean(false);

                    @Override
                    public void request(long n) {
                        if (requested.compareAndSet(false, true)) {
                            subscriber.onNext(1);
                        } else {
                            subscriber.onComplete();
                        }
                    }
                });
                producer.set(p);
                subscriber.setProducer(p);
            }
        }).scan(100, new BiFunction<Integer, Integer, Integer>() {

            @Override
            public Integer call(Integer t1, Integer t2) {
                return t1 + t2;
            }

        });

        o.subscribe(new TestSubscriber<Integer>() {

            @Override
            public void onStart() {
                request(1);
            }

            @Override
            public void onNext(Integer integer) {
                request(1);
            }
        });

        verify(producer.get(), never()).request(0);
        verify(producer.get(), times(2)).request(1);
    }
}
