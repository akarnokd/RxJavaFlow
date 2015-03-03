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
package rx.observables;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Subscriber;
import rx.exceptions.TestException;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Function;
import rx.schedulers.Schedulers;
import rx.subscriptions.Subscriptions;

public class BlockingFlowableTest {

    @Mock
    Subscriber<Integer> w;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testLast() {
        BlockingFlowable<String> obs = BlockingFlowable.from(Flowable.just("one", "two", "three"));

        assertEquals("three", obs.last());
    }

    @Test(expected = NoSuchElementException.class)
    public void testLastEmptyFlowable() {
        BlockingFlowable<Object> obs = BlockingFlowable.from(Flowable.empty());
        obs.last();
    }

    @Test
    public void testLastOrDefault() {
        BlockingFlowable<Integer> observable = BlockingFlowable.from(Flowable.just(1, 0, -1));
        int last = observable.lastOrDefault(-100, new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer args) {
                return args >= 0;
            }
        });
        assertEquals(0, last);
    }

    @Test
    public void testLastOrDefault1() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        assertEquals("three", observable.lastOrDefault("default"));
    }

    @Test
    public void testLastOrDefault2() {
        BlockingFlowable<Object> observable = BlockingFlowable.from(Flowable.empty());
        assertEquals("default", observable.lastOrDefault("default"));
    }

    @Test
    public void testLastOrDefaultWithPredicate() {
        BlockingFlowable<Integer> observable = BlockingFlowable.from(Flowable.just(1, 0, -1));
        int last = observable.lastOrDefault(0, new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer args) {
                return args < 0;
            }
        });

        assertEquals(-1, last);
    }

    @Test
    public void testLastOrDefaultWrongPredicate() {
        BlockingFlowable<Integer> observable = BlockingFlowable.from(Flowable.just(-1, -2, -3));
        int last = observable.lastOrDefault(0, new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer args) {
                return args >= 0;
            }
        });
        assertEquals(0, last);
    }

    @Test
    public void testLastWithPredicate() {
        BlockingFlowable<String> obs = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        assertEquals("two", obs.last(new Function<String, Boolean>() {
            @Override
            public Boolean call(String s) {
                return s.length() == 3;
            }
        }));
    }

    @Test
    public void testSingle() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one"));
        assertEquals("one", observable.single());
    }

    @Test
    public void testSingleDefault() {
        BlockingFlowable<Object> observable = BlockingFlowable.from(Flowable.empty());
        assertEquals("default", observable.singleOrDefault("default"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSingleDefaultPredicateMatchesMoreThanOne() {
        BlockingFlowable.from(Flowable.just("one", "two")).singleOrDefault("default", new Function<String, Boolean>() {
            @Override
            public Boolean call(String args) {
                return args.length() == 3;
            }
        });
    }

    @Test
    public void testSingleDefaultPredicateMatchesNothing() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two"));
        String result = observable.singleOrDefault("default", new Function<String, Boolean>() {
            @Override
            public Boolean call(String args) {
                return args.length() == 4;
            }
        });
        assertEquals("default", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSingleDefaultWithMoreThanOne() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        observable.singleOrDefault("default");
    }

    @Test
    public void testSingleWithPredicateDefault() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "four"));
        assertEquals("four", observable.single(new Function<String, Boolean>() {
            @Override
            public Boolean call(String s) {
                return s.length() == 4;
            }
        }));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSingleWrong() {
        BlockingFlowable<Integer> observable = BlockingFlowable.from(Flowable.just(1, 2));
        observable.single();
    }

    @Test(expected = NoSuchElementException.class)
    public void testSingleWrongPredicate() {
        BlockingFlowable<Integer> observable = BlockingFlowable.from(Flowable.just(-1));
        observable.single(new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer args) {
                return args > 0;
            }
        });
    }

    @Test
    public void testToIterable() {
        BlockingFlowable<String> obs = BlockingFlowable.from(Flowable.just("one", "two", "three"));

        Iterator<String> it = obs.toIterable().iterator();

        assertEquals(true, it.hasNext());
        assertEquals("one", it.next());

        assertEquals(true, it.hasNext());
        assertEquals("two", it.next());

        assertEquals(true, it.hasNext());
        assertEquals("three", it.next());

        assertEquals(false, it.hasNext());

    }

    @Test(expected = NoSuchElementException.class)
    public void testToIterableNextOnly() {
        BlockingFlowable<Integer> obs = BlockingFlowable.from(Flowable.just(1, 2, 3));

        Iterator<Integer> it = obs.toIterable().iterator();

        Assert.assertEquals((Integer) 1, it.next());
        Assert.assertEquals((Integer) 2, it.next());
        Assert.assertEquals((Integer) 3, it.next());

        it.next();
    }

    @Test(expected = NoSuchElementException.class)
    public void testToIterableNextOnlyTwice() {
        BlockingFlowable<Integer> obs = BlockingFlowable.from(Flowable.just(1, 2, 3));

        Iterator<Integer> it = obs.toIterable().iterator();

        Assert.assertEquals((Integer) 1, it.next());
        Assert.assertEquals((Integer) 2, it.next());
        Assert.assertEquals((Integer) 3, it.next());

        boolean exc = false;
        try {
            it.next();
        } catch (NoSuchElementException ex) {
            exc = true;
        }
        Assert.assertEquals(true, exc);

        it.next();
    }

    @Test
    public void testToIterableManyTimes() {
        BlockingFlowable<Integer> obs = BlockingFlowable.from(Flowable.just(1, 2, 3));

        Iterable<Integer> iter = obs.toIterable();

        for (int j = 0; j < 3; j++) {
            Iterator<Integer> it = iter.iterator();

            Assert.assertTrue(it.hasNext());
            Assert.assertEquals((Integer) 1, it.next());
            Assert.assertTrue(it.hasNext());
            Assert.assertEquals((Integer) 2, it.next());
            Assert.assertTrue(it.hasNext());
            Assert.assertEquals((Integer) 3, it.next());
            Assert.assertFalse(it.hasNext());
        }
    }

    @Test(expected = TestException.class)
    public void testToIterableWithException() {
        BlockingFlowable<String> obs = BlockingFlowable.from(Flowable.create(new Flowable.OnSubscribe<String>() {

            @Override
            public void call(Subscriber<? super String> observer) {
                observer.onNext("one");
                observer.onError(new TestException());
            }
        }));

        Iterator<String> it = obs.toIterable().iterator();

        assertEquals(true, it.hasNext());
        assertEquals("one", it.next());

        assertEquals(true, it.hasNext());
        it.next();

    }

    @Test
    public void testForEachWithError() {
        try {
            BlockingFlowable.from(Flowable.create(new Flowable.OnSubscribe<String>() {

                @Override
                public void call(final Subscriber<? super String> observer) {
                    new Thread(new Runnable() {

                        @Override
                        public void run() {
                            observer.onNext("one");
                            observer.onNext("two");
                            observer.onNext("three");
                            observer.onComplete();
                        }
                    }).start();
                }
            })).forEach(new Action1<String>() {

                @Override
                public void call(String t1) {
                    throw new RuntimeException("fail");
                }
            });
            fail("we expect an exception to be thrown");
        } catch (Throwable e) {
            // do nothing as we expect this
        }
    }

    @Test
    public void testFirst() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        assertEquals("one", observable.first());
    }

    @Test(expected = NoSuchElementException.class)
    public void testFirstWithEmpty() {
        BlockingFlowable.from(Flowable.<String> empty()).first();
    }

    @Test
    public void testFirstWithPredicate() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        String first = observable.first(new Function<String, Boolean>() {
            @Override
            public Boolean call(String args) {
                return args.length() > 3;
            }
        });
        assertEquals("three", first);
    }

    @Test(expected = NoSuchElementException.class)
    public void testFirstWithPredicateAndEmpty() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        observable.first(new Function<String, Boolean>() {
            @Override
            public Boolean call(String args) {
                return args.length() > 5;
            }
        });
    }

    @Test
    public void testFirstOrDefault() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        assertEquals("one", observable.firstOrDefault("default"));
    }

    @Test
    public void testFirstOrDefaultWithEmpty() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.<String> empty());
        assertEquals("default", observable.firstOrDefault("default"));
    }

    @Test
    public void testFirstOrDefaultWithPredicate() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        String first = observable.firstOrDefault("default", new Function<String, Boolean>() {
            @Override
            public Boolean call(String args) {
                return args.length() > 3;
            }
        });
        assertEquals("three", first);
    }

    @Test
    public void testFirstOrDefaultWithPredicateAndEmpty() {
        BlockingFlowable<String> observable = BlockingFlowable.from(Flowable.just("one", "two", "three"));
        String first = observable.firstOrDefault("default", new Function<String, Boolean>() {
            @Override
            public Boolean call(String args) {
                return args.length() > 5;
            }
        });
        assertEquals("default", first);
    }

    @Test
    public void testSingleOrDefaultUnsubscribe() throws InterruptedException {
        final CountDownLatch unsubscribe = new CountDownLatch(1);
        Flowable<Integer> o = Flowable.create(new OnSubscribe<Integer>() {
            @Override
            public void call(Subscriber<? super Integer> subscriber) {
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        unsubscribe.countDown();
                    }
                }));
                subscriber.onNext(1);
                subscriber.onNext(2);
                // Don't call `onComplete()` to emulate an infinite stream
            }
        }).subscribeOn(Schedulers.newThread());
        try {
            o.toBlocking().singleOrDefault(-1);
            fail("Expected IllegalArgumentException because there are 2 elements");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        assertTrue("Timeout means `unsubscribe` is not called", unsubscribe.await(30, TimeUnit.SECONDS));
    }

    @Test
    public void testUnsubscribeFromSingleWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("single()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.single();
            }
        });
    }

    @Test
    public void testUnsubscribeFromForEachWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("forEach()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.forEach(new Action1<Void>() {
                    @Override
                    public void call(final Void aVoid) {
                        // nothing
                    }
                });
            }
        });
    }

    @Test
    public void testUnsubscribeFromFirstWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("first()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.first();
            }
        });
    }

    @Test
    public void testUnsubscribeFromLastWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("last()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.last();
            }
        });
    }

    @Test
    public void testUnsubscribeFromLatestWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("latest()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.latest().iterator().next();
            }
        });
    }

    @Test
    public void testUnsubscribeFromNextWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("next()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.next().iterator().next();
            }
        });
    }

    @Test
    public void testUnsubscribeFromGetIteratorWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("getIterator()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.getIterator().next();
            }
        });
    }

    @Test
    public void testUnsubscribeFromToIterableWhenInterrupted() throws InterruptedException {
        new InterruptionTests().assertUnsubscribeIsInvoked("toIterable()", new Action1<BlockingFlowable<Void>>() {
            @Override
            public void call(final BlockingFlowable<Void> o) {
                o.toIterable().iterator().next();
            }
        });
    }

    /** Utilities set for interruption behaviour tests. */
    private static class InterruptionTests {

        private boolean isUnSubscribed;
        private RuntimeException error;
        private CountDownLatch latch = new CountDownLatch(1);

        private Flowable<Void> createFlowable() {
            return Flowable.<Void>never().doOnUnsubscribe(new Action0() {
                @Override
                public void call() {
                    isUnSubscribed = true;
                }
            });
        }

        private void startBlockingAndInterrupt(final Action1<BlockingFlowable<Void>> blockingAction) {
            Thread subscriptionThread = new Thread() {
                @Override
                public void run() {
                    try {
                        blockingAction.call(createFlowable().toBlocking());
                    } catch (RuntimeException e) {
                        if (!(e.getCause() instanceof InterruptedException)) {
                            error = e;
                        }
                    }
                    latch.countDown();
                }
            };
            subscriptionThread.start();
            subscriptionThread.interrupt();
        }

        void assertUnsubscribeIsInvoked(final String method, final Action1<BlockingFlowable<Void>> blockingAction)
            throws InterruptedException {
            startBlockingAndInterrupt(blockingAction);
            assertTrue("Timeout means interruption is not performed", latch.await(30, TimeUnit.SECONDS));
            if (error != null) {
                throw error;
            }
            assertTrue("'unsubscribe' is not invoked when thread is interrupted for " + method, isUnSubscribed);
        }

    }

}
