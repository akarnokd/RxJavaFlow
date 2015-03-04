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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import rx.Flowable;
import rx.functions.Action1;
import rx.functions.Function;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

public class OperatorWindowWithSizeTest {

    private static <T> List<List<T>> toLists(Flowable<Flowable<T>> observables) {

        final List<List<T>> lists = new ArrayList<List<T>>();
        Flowable.concat(observables.map(new Function<Flowable<T>, Flowable<List<T>>>() {
            @Override
            public Flowable<List<T>> call(Flowable<T> xs) {
                return xs.toList();
            }
        }))
                .toBlocking()
                .forEach(new Action1<List<T>>() {
                    @Override
                    public void call(List<T> xs) {
                        lists.add(xs);
                    }
                });
        return lists;
    }

    @Test
    public void testNonOverlappingWindows() {
        Flowable<String> subject = Flowable.just("one", "two", "three", "four", "five");
        Flowable<Flowable<String>> windowed = subject.window(3);

        List<List<String>> windows = toLists(windowed);

        assertEquals(2, windows.size());
        assertEquals(list("one", "two", "three"), windows.get(0));
        assertEquals(list("four", "five"), windows.get(1));
    }

    @Test
    public void testSkipAndCountGaplessWindows() {
        Flowable<String> subject = Flowable.just("one", "two", "three", "four", "five");
        Flowable<Flowable<String>> windowed = subject.window(3, 3);

        List<List<String>> windows = toLists(windowed);

        assertEquals(2, windows.size());
        assertEquals(list("one", "two", "three"), windows.get(0));
        assertEquals(list("four", "five"), windows.get(1));
    }

    @Test
    public void testOverlappingWindows() {
        Flowable<String> subject = Flowable.from(new String[] { "zero", "one", "two", "three", "four", "five" });
        Flowable<Flowable<String>> windowed = subject.window(3, 1);

        List<List<String>> windows = toLists(windowed);

        assertEquals(6, windows.size());
        assertEquals(list("zero", "one", "two"), windows.get(0));
        assertEquals(list("one", "two", "three"), windows.get(1));
        assertEquals(list("two", "three", "four"), windows.get(2));
        assertEquals(list("three", "four", "five"), windows.get(3));
        assertEquals(list("four", "five"), windows.get(4));
        assertEquals(list("five"), windows.get(5));
    }

    @Test
    public void testSkipAndCountWindowsWithGaps() {
        Flowable<String> subject = Flowable.just("one", "two", "three", "four", "five");
        Flowable<Flowable<String>> windowed = subject.window(2, 3);

        List<List<String>> windows = toLists(windowed);

        assertEquals(2, windows.size());
        assertEquals(list("one", "two"), windows.get(0));
        assertEquals(list("four", "five"), windows.get(1));
    }

    @Test
    public void testWindowUnsubscribeNonOverlapping() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        final AtomicInteger count = new AtomicInteger();
        Flowable.merge(Flowable.range(1, 10000).doOnNext(new Action1<Integer>() {

            @Override
            public void call(Integer t1) {
                count.incrementAndGet();
            }

        }).window(5).take(2)).subscribe(ts);
        ts.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
        ts.assertValues((1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        //        System.out.println(ts.getOnNextEvents());
        assertEquals(10, count.get());
    }

    @Test
    public void testWindowUnsubscribeNonOverlappingAsyncSource() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        final AtomicInteger count = new AtomicInteger();
        Flowable.merge(Flowable.range(1, 100000)
                .doOnNext(new Action1<Integer>() {

                    @Override
                    public void call(Integer t1) {
                        count.incrementAndGet();
                    }

                })
                .observeOn(Schedulers.computation())
                .window(5)
                .take(2))
                .subscribe(ts);
        ts.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
        ts.assertValues((1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        // make sure we don't emit all values ... the unsubscribe should propagate
        assertTrue(count.get() < 100000);
    }

    @Test
    public void testWindowUnsubscribeOverlapping() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        final AtomicInteger count = new AtomicInteger();
        Flowable.merge(Flowable.range(1, 10000).doOnNext(new Action1<Integer>() {

            @Override
            public void call(Integer t1) {
                count.incrementAndGet();
            }

        }).window(5, 4).take(2)).subscribe(ts);
        ts.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
        //        System.out.println(ts.getOnNextEvents());
        ts.assertValues((1, 2, 3, 4, 5, 5, 6, 7, 8, 9));
        assertEquals(9, count.get());
    }

    @Test
    public void testWindowUnsubscribeOverlappingAsyncSource() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        final AtomicInteger count = new AtomicInteger();
        Flowable.merge(Flowable.range(1, 100000)
                .doOnNext(new Action1<Integer>() {

                    @Override
                    public void call(Integer t1) {
                        count.incrementAndGet();
                    }

                })
                .observeOn(Schedulers.computation())
                .window(5, 4)
                .take(2))
                .subscribe(ts);
        ts.awaitTerminalEvent(500, TimeUnit.MILLISECONDS);
        ts.assertTerminalEvent();
        ts.assertValues((1, 2, 3, 4, 5, 5, 6, 7, 8, 9));
        // make sure we don't emit all values ... the unsubscribe should propagate
        assertTrue(count.get() < 100000);
    }

    private List<String> list(String... args) {
        List<String> list = new ArrayList<String>();
        for (String arg : args) {
            list.add(arg);
        }
        return list;
    }

}