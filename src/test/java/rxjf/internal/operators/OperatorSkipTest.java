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

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Test;

import rx.Flowable;
import rx.Observer;
import rx.internal.operators.OperatorSkip;

public class OperatorSkipTest {

    @Test
    public void testSkipNegativeElements() {

        Flowable<String> skip = Flowable.just("one", "two", "three").lift(new OperatorSkip<String>(-99));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        skip.subscribe(observer);
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipZeroElements() {

        Flowable<String> skip = Flowable.just("one", "two", "three").lift(new OperatorSkip<String>(0));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        skip.subscribe(observer);
        verify(observer, times(1)).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipOneElement() {

        Flowable<String> skip = Flowable.just("one", "two", "three").lift(new OperatorSkip<String>(1));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        skip.subscribe(observer);
        verify(observer, never()).onNext("one");
        verify(observer, times(1)).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipTwoElements() {

        Flowable<String> skip = Flowable.just("one", "two", "three").lift(new OperatorSkip<String>(2));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        skip.subscribe(observer);
        verify(observer, never()).onNext("one");
        verify(observer, never()).onNext("two");
        verify(observer, times(1)).onNext("three");
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipEmptyStream() {

        Flowable<String> w = Flowable.empty();
        Flowable<String> skip = w.lift(new OperatorSkip<String>(1));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        skip.subscribe(observer);
        verify(observer, never()).onNext(any(String.class));
        verify(observer, never()).onError(any(Throwable.class));
        verify(observer, times(1)).onComplete();
    }

    @Test
    public void testSkipMultipleObservers() {

        Flowable<String> skip = Flowable.just("one", "two", "three").lift(new OperatorSkip<String>(2));

        @SuppressWarnings("unchecked")
        Observer<String> observer1 = mock(Observer.class);
        skip.subscribe(observer1);

        @SuppressWarnings("unchecked")
        Observer<String> observer2 = mock(Observer.class);
        skip.subscribe(observer2);

        verify(observer1, times(1)).onNext(any(String.class));
        verify(observer1, never()).onError(any(Throwable.class));
        verify(observer1, times(1)).onComplete();

        verify(observer2, times(1)).onNext(any(String.class));
        verify(observer2, never()).onError(any(Throwable.class));
        verify(observer2, times(1)).onComplete();
    }

    @Test
    public void testSkipError() {

        Exception e = new Exception();

        Flowable<String> ok = Flowable.just("one");
        Flowable<String> error = Flowable.error(e);

        Flowable<String> skip = Flowable.concat(ok, error).lift(new OperatorSkip<String>(100));

        @SuppressWarnings("unchecked")
        Observer<String> observer = mock(Observer.class);
        skip.subscribe(observer);

        verify(observer, never()).onNext(any(String.class));
        verify(observer, times(1)).onError(e);
        verify(observer, never()).onComplete();

    }
}
