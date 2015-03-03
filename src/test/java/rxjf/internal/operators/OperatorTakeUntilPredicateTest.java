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
import static org.mockito.Mockito.*;

import java.util.Arrays;

import org.junit.*;

import rx.*;
import rx.exceptions.TestException;
import rx.functions.Function;
import rx.internal.util.UtilityFunctions;
import rx.observers.TestSubscriber;
;

public class OperatorTakeUntilPredicateTest {
    @Test
    public void takeEmpty() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        Flowable.empty().takeUntil(UtilityFunctions.alwaysTrue()).subscribe(o);
        
        verify(o, never()).onNext(any());
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeAll() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1, 2).takeUntil(UtilityFunctions.alwaysFalse()).subscribe(o);
        
        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeFirst() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1, 2).takeUntil(UtilityFunctions.alwaysTrue()).subscribe(o);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void takeSome() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1, 2, 3).takeUntil(new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer t1) {
                return t1 == 2;
            }
        }).subscribe(o);
        
        verify(o).onNext(1);
        verify(o).onNext(2);
        verify(o, never()).onNext(3);
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void functionThrows() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1, 2, 3).takeUntil(new Function<Integer, Boolean>() {
            @Override
            public Boolean call(Integer t1) {
                throw new TestException("Forced failure");
            }
        }).subscribe(o);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o, never()).onNext(3);
        verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
    }
    @Test
    public void sourceThrows() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        Flowable.just(1)
        .concatWith(Flowable.<Integer>error(new TestException()))
        .concatWith(Flowable.just(2))
        .takeUntil(UtilityFunctions.alwaysFalse()).subscribe(o);
        
        verify(o).onNext(1);
        verify(o, never()).onNext(2);
        verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
    }
    @Test
    public void backpressure() {
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>() {
            @Override
            public void onStart() {
                request(5);
            }
        };
        
        Flowable.range(1, 1000).takeUntil(UtilityFunctions.alwaysFalse()).subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertReceivedOnNext(Arrays.asList(1, 2, 3, 4, 5));
        Assert.assertEquals(0, ts.getonComplete()Events().size());
    }
}
