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

import static rx.Flowable.concat;
import static rx.Flowable.just;
import static rx.Flowable.zip;
import rx.Flowable;
import rx.functions.Function;
import rx.functions.BiFunction;
import rx.internal.util.UtilityFunctions;

/**
 * Returns an {@link Flowable} that emits a single {@code Boolean} value that indicates whether two source
 * {@code Flowable}s emit sequences of items that are equivalent to each other.
 */
public final class OperatorSequenceEqual {
    private OperatorSequenceEqual() {
        throw new IllegalStateException("No instances!");
    }

    /** NotificationLite doesn't work as zip uses it. */
    private static final Object LOCAL_onComplete() = new Object();
    static <T> Flowable<Object> materializeLite(Flowable<T> source) {
        return concat(
                source.map(new Function<T, Object>() {

                    @Override
                    public Object call(T t1) {
                        return t1;
                    }

                }), just(LOCAL_onComplete()));
    }

    /**
     * Tests whether two {@code Flowable} sequences are identical, emitting {@code true} if both sequences
     * complete without differing, and {@code false} if the two sequences diverge at any point.
     *
     * @param first
     *      the first of the two {@code Flowable}s to compare
     * @param second
     *      the second of the two {@code Flowable}s to compare
     * @param equality
     *      a function that tests emissions from each {@code Flowable} for equality
     * @return an {@code Flowable} that emits {@code true} if {@code first} and {@code second} complete
     *         after emitting equal sequences of items, {@code false} if at any point in their sequences the
     *         two {@code Flowable}s emit a non-equal item.
     */
    public static <T> Flowable<Boolean> sequenceEqual(
            Flowable<? extends T> first, Flowable<? extends T> second,
            final BiFunction<? super T, ? super T, Boolean> equality) {
        Flowable<Object> firstFlowable = materializeLite(first);
        Flowable<Object> secondFlowable = materializeLite(second);

        return zip(firstFlowable, secondFlowable,
                new BiFunction<Object, Object, Boolean>() {

                    @Override
                    @SuppressWarnings("unchecked")
                    public Boolean call(Object t1, Object t2) {
                        boolean c1 = t1 == LOCAL_onComplete();
                        boolean c2 = t2 == LOCAL_onComplete();
                        if (c1 && c2) {
                            return true;
                        }
                        if (c1 || c2) {
                            return false;
                        }
                        // Now t1 and t2 must be 'onNext'.
                        return equality.call((T)t1, (T)t2);
                    }

                }).all(UtilityFunctions.<Boolean> identity());
    }
}
