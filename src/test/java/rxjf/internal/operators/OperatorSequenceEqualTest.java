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

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import org.junit.Test;
import org.mockito.InOrder;

import rx.Flowable;
import rx.Observer;
import rx.exceptions.TestException;
import rx.functions.BiFunction;

public class OperatorSequenceEqualTest {

    @Test
    public void test1() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just("one", "two", "three"),
                Flowable.just("one", "two", "three"));
        verifyResult(observable, true);
    }

    @Test
    public void test2() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just("one", "two", "three"),
                Flowable.just("one", "two", "three", "four"));
        verifyResult(observable, false);
    }

    @Test
    public void test3() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just("one", "two", "three", "four"),
                Flowable.just("one", "two", "three"));
        verifyResult(observable, false);
    }

    @Test
    public void testWithError1() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.concat(Flowable.just("one"),
                        Flowable.<String> error(new TestException())),
                Flowable.just("one", "two", "three"));
        verifyError(observable);
    }

    @Test
    public void testWithError2() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just("one", "two", "three"),
                Flowable.concat(Flowable.just("one"),
                        Flowable.<String> error(new TestException())));
        verifyError(observable);
    }

    @Test
    public void testWithError3() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.concat(Flowable.just("one"),
                        Flowable.<String> error(new TestException())),
                Flowable.concat(Flowable.just("one"),
                        Flowable.<String> error(new TestException())));
        verifyError(observable);
    }

    @Test
    public void testWithEmpty1() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.<String> empty(),
                Flowable.just("one", "two", "three"));
        verifyResult(observable, false);
    }

    @Test
    public void testWithEmpty2() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just("one", "two", "three"),
                Flowable.<String> empty());
        verifyResult(observable, false);
    }

    @Test
    public void testWithEmpty3() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.<String> empty(), Flowable.<String> empty());
        verifyResult(observable, true);
    }

    @Test
    public void testWithNull1() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just((String) null), Flowable.just("one"));
        verifyResult(observable, false);
    }

    @Test
    public void testWithNull2() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just((String) null), Flowable.just((String) null));
        verifyResult(observable, true);
    }

    @Test
    public void testWithEqualityError() {
        Flowable<Boolean> observable = Flowable.sequenceEqual(
                Flowable.just("one"), Flowable.just("one"),
                new BiFunction<String, String, Boolean>() {
                    @Override
                    public Boolean call(String t1, String t2) {
                        throw new TestException();
                    }
                });
        verifyError(observable);
    }

    private void verifyResult(Flowable<Boolean> observable, boolean result) {
        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onNext(result);
        inOrder.verify(observer).onComplete();
        inOrder.verifyNoMoreInteractions();
    }

    private void verifyError(Flowable<Boolean> observable) {
        @SuppressWarnings("unchecked")
        Observer<Boolean> observer = mock(Observer.class);
        observable.subscribe(observer);

        InOrder inOrder = inOrder(observer);
        inOrder.verify(observer, times(1)).onError(isA(TestException.class));
        inOrder.verifyNoMoreInteractions();
    }
}
