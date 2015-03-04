/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rxjf.internal.operators;

import java.util.concurrent.atomic.AtomicReference;

import rx.*;
import rx.Flowable.Operator;
import rx.functions.BiFunction;
import rx.observers.SerializedSubscriber;

/**
 * Combines values from two sources only when the main source emits.
 * @param <T> the element type of the main observable
 * @param <U> the element type of the other observable that is merged into the main
 * @param <R> the result element type
 */
public final class OperatorWithLatestFrom<T, U, R> implements Operator<R, T>  {
    final BiFunction<? super T, ? super U, ? extends R> resultSelector;
    final Flowable<? extends U> other;
    /** Indicates the other has not yet emitted a value. */
    static final Object EMPTY = new Object();
    
    public OperatorWithLatestFrom(Flowable<? extends U> other, BiFunction<? super T, ? super U, ? extends R> resultSelector) {
        this.other = other;
        this.resultSelector = resultSelector;
    }
    @Override
    public Subscriber<? super T> call(Subscriber<? super R> child) {
        // onError and onComplete() may happen either from the main or from other.
        final SerializedSubscriber<R> s = new SerializedSubscriber<R>(child, false);
        child.add(s);
        
        final AtomicReference<Object> current = new AtomicReference<Object>(EMPTY);
        
        final Subscriber<T> subscriber = new Subscriber<T>(s, true) {
            @Override
            public void onNext(T t) {
                Object o = current.get();
                if (o != EMPTY) {
                    try {
                        @SuppressWarnings("unchecked")
                        U u = (U)o;
                        R result = resultSelector.call(t, u);
                        
                        s.onNext(result);
                    } catch (Throwable e) {
                        onError(e);
                        return;
                    }
                }
            }
            @Override
            public void onError(Throwable e) {
                s.onError(e);
                s.unsubscribe();
            }
            @Override
            public void onComplete() {
                s.onComplete();
                s.unsubscribe();
            }
        };
        
        Subscriber<U> otherSubscriber = new Subscriber<U>() {
            @Override
            public void onNext(U t) {
                current.set(t);
            }
            @Override
            public void onError(Throwable e) {
                s.onError(e);
                s.unsubscribe();
            }
            @Override
            public void onComplete() {
                if (current.get() == EMPTY) {
                    s.onComplete();
                    s.unsubscribe();
                }
            }
        };
        s.add(subscriber);
        s.add(otherSubscriber);
        
        other.unsafeSubscribe(otherSubscriber);
        
        return subscriber;
    }
}
