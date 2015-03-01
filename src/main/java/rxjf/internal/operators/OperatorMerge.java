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

import rxjf.Flow.Subscriber;
import rxjf.*;
import rxjf.Flowable.Operator;

/**
 * 
 */
public final class OperatorMerge<T> implements Operator<T, Flowable<? extends T>> {
    final boolean delayErrors;
    final int maxConcurrent;
    /** Lazy initialization via inner-class holder. */
    private static final class HolderNoDelay {
        /** A singleton instance. */
        static final OperatorMerge<Object> INSTANCE = new OperatorMerge<>(false, Integer.MAX_VALUE);
    }
    /** Lazy initialization via inner-class holder. */
    private static final class HolderDelayErrors {
        /** A singleton instance. */
        static final OperatorMerge<Object> INSTANCE = new OperatorMerge<>(true, Integer.MAX_VALUE);
    }

    /**
     * @param delayErrors should the merge delay errors?
     * @return a singleton instance of this stateless operator.
     */
    @SuppressWarnings("unchecked")
    public static <T> OperatorMerge<T> instance(boolean delayErrors) {
        if (delayErrors) {
            return (OperatorMerge<T>)HolderDelayErrors.INSTANCE;
        }
        return (OperatorMerge<T>)HolderNoDelay.INSTANCE;
    }
    
    public static <T> OperatorMerge<T> instance(boolean delayErrors, int maxConcurrent) {
        return new OperatorMerge<>(delayErrors, maxConcurrent);
    }
    
    private OperatorMerge(boolean delayErrors, int maxConcurrent) {
        this.delayErrors = delayErrors;
        this.maxConcurrent = maxConcurrent;
    }
    @Override
    public Subscriber<? super Flowable<? extends T>> apply(Subscriber<? super T> child) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException();
    }
}
