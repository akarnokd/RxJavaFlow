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

import rx.Flow.Subscriber;
import rx.Observable.Operator;
import rx.subscribers.SerializedSubscriber;

public final class OperatorSerialize<T> implements Operator<T, T> {
    /** Lazy initialization via inner-class holder. */
    private static final class Holder {
        /** A singleton instance. */
        static final OperatorSerialize<Object> INSTANCE = new OperatorSerialize<>();
    }
    /**
     * @return a singleton instance of this stateless operator.
     */
    @SuppressWarnings({ "unchecked" })
    public static <T> OperatorSerialize<T> instance() {
        return (OperatorSerialize<T>)Holder.INSTANCE;
    }
    private OperatorSerialize() { }
    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> s) {
        return SerializedSubscriber.wrap(s);
    }

}
