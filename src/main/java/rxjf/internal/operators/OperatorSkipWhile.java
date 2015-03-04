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

import rx.Flowable.Operator;
import rx.Subscriber;
import rx.functions.Function;
import rx.functions.BiFunction;

/**
 * Skips any emitted source items as long as the specified condition holds true. Emits all further source items
 * as soon as the condition becomes false.
 */
public final class OperatorSkipWhile<T> implements Operator<T, T> {
    private final BiFunction<? super T, Integer, Boolean> predicate;

    public OperatorSkipWhile(BiFunction<? super T, Integer, Boolean> predicate) {
        this.predicate = predicate;
    }
    @Override
    public Subscriber<? super T> call(final Subscriber<? super T> child) {
        return new Subscriber<T>(child) {
            boolean skipping = true;
            int index;
            @Override
            public void onNext(T t) {
                if (!skipping) {
                    child.onNext(t);
                } else {
                    if (!predicate.call(t, index++)) {
                        skipping = false;
                        child.onNext(t);
                    } else {
                        request(1);
                    }
                }
            }

            @Override
            public void onError(Throwable e) {
                child.onError(e);
            }

            @Override
            public void onComplete() {
                child.onComplete();
            }
        };
    }
    /** Convert to BiFunction type predicate. */
    public static <T> BiFunction<T, Integer, Boolean> toPredicate2(final Function<? super T, Boolean> predicate) {
        return new BiFunction<T, Integer, Boolean>() {

            @Override
            public Boolean call(T t1, Integer t2) {
                return predicate.call(t1);
            }
        };
    }
}
