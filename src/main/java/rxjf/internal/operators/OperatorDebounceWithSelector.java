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

import rx.Flowable;
import rx.Flowable.Operator;
import rx.Subscriber;
import rx.functions.Function;
import rx.internal.operators.OperatorDebounceWithTime.DebounceState;
import rx.observers.SerializedSubscriber;
import rx.subscriptions.SerialSubscription;

/**
 * Delay the emission via another observable if no new source appears in the meantime.
 * 
 * @param <T> the value type of the main sequence
 * @param <U> the value type of the boundary sequence
 */
public final class OperatorDebounceWithSelector<T, U> implements Operator<T, T> {
    final Function<? super T, ? extends Flowable<U>> selector;
    
    public OperatorDebounceWithSelector(Function<? super T, ? extends Flowable<U>> selector) {
        this.selector = selector;
    }
    
    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> child) {
        final SerializedSubscriber<T> s = new SerializedSubscriber<T>(child);
        final SerialSubscription ssub = new SerialSubscription();
        child.add(ssub);
        
        return new Subscriber<T>(child) {
            final DebounceState<T> state = new DebounceState<T>();
            final Subscriber<?> self = this;
            
            @Override
            public void onStart() {
                // debounce wants to receive everything as a firehose without backpressure
                request(Long.MAX_VALUE);
            }
            
            @Override
            public void onNext(T t) {
                Flowable<U> debouncer;
                
                try {
                    debouncer = selector.call(t);
                } catch (Throwable e) {
                    onError(e);
                    return;
                }
                
                
                final int index = state.next(t);
                
                Subscriber<U> debounceSubscriber = new Subscriber<U>() {

                    @Override
                    public void onNext(U t) {
                        onComplete();
                    }

                    @Override
                    public void onError(Throwable e) {
                        self.onError(e);
                    }

                    @Override
                    public void onComplete() {
                        state.emit(index, s, self);
                        unsubscribe();
                    }
                };
                ssub.set(debounceSubscriber);
                
                debouncer.unsafeSubscribe(debounceSubscriber);
                
            }

            @Override
            public void onError(Throwable e) {
                s.onError(e);
                unsubscribe();
                state.clear();
            }

            @Override
            public void onComplete() {
                state.emitAndComplete(s, this);
            }
        };
    }
    
}