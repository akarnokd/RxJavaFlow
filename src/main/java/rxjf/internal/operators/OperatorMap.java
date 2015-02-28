/**
 * Copyright 2015 David Karnok
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rxjf.internal.operators;

import java.util.function.Function;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;

/**
 * 
 */
public final class OperatorMap<T, R> implements Operator<T, R> {
    final Function<? super T, ? extends R> function;
    public OperatorMap(Function<? super T, ? extends R> function) {
        this.function = function;
    }
    @Override
    public Subscriber<? super T> apply(Subscriber<? super R> t) {
        return new Subscriber<T>() {
            @Override
            public void onSubscribe(Subscription subscription) {
                t.onSubscribe(subscription);
            }
            @Override
            public void onNext(T item) {
                R r;
                try {
                    r = function.apply(item);
                } catch (Throwable e) {
                    onError(e);
                    return;
                }
                t.onNext(r);
            }
            @Override
            public void onError(Throwable throwable) {
                t.onError(throwable);
            }
            @Override
            public void onComplete() {
                t.onComplete();
            }
        };
    }
}
