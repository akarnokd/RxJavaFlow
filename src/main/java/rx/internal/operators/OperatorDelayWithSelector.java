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
package rx.internal.operators;

import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;
import rx.functions.Function;
import rx.observers.SerializedSubscriber;
import rx.subjects.PublishSubject;

/**
 * Delay the subscription and emission of the source items by a per-item observable that fires its first element.
 * 
 * @param <T>
 *            the item type
 * @param <V>
 *            the value type of the item-delaying observable
 */
public final class OperatorDelayWithSelector<T, V> implements Operator<T, T> {
    final Observable<? extends T> source;
    final Function<? super T, ? extends Observable<V>> itemDelay;

    public OperatorDelayWithSelector(Observable<? extends T> source, Function<? super T, ? extends Observable<V>> itemDelay) {
        this.source = source;
        this.itemDelay = itemDelay;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> _child) {
        final SerializedSubscriber<T> child = new SerializedSubscriber<T>(_child);
        final PublishSubject<Observable<T>> delayedEmissions = PublishSubject.create();

        _child.add(Observable.merge(delayedEmissions).unsafeSubscribe(new Subscriber<T>() {

            @Override
            public void onComplete() {
                child.onComplete();
            }

            @Override
            public void onError(Throwable e) {
                child.onError(e);
            }

            @Override
            public void onNext(T t) {
                child.onNext(t);
            }

        }));

        return new Subscriber<T>(_child) {

            @Override
            public void onComplete() {
                delayedEmissions.onComplete();
            }

            @Override
            public void onError(Throwable e) {
                child.onError(e);
            }

            @Override
            public void onNext(final T t) {
                try {
                    delayedEmissions.onNext(itemDelay.call(t).take(1).defaultIfEmpty(null).map(new Function<V, T>() {

                        @Override
                        public T call(V v) {
                            return t;
                        }

                    }));
                } catch (Throwable e) {
                    onError(e);
                }
            }

        };
    }
}
