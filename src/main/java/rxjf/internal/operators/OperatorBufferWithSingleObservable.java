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

import java.util.ArrayList;
import java.util.List;

import rx.Flowable;
import rx.Flowable.Operator;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Supplier;
import rx.observers.SerializedSubscriber;
import rx.observers.Subscribers;

/**
 * This operation takes
 * values from the specified {@link Flowable} source and stores them in a buffer until the
 * {@link Flowable} constructed using the {@link Supplier} argument, produces a value. The buffer is then
 * emitted, and a new buffer is created to replace it. A new {@link Flowable} will be constructed using
 * the provided {@link Supplier} object, which will determine when this new buffer is emitted. When the source
 * {@link Flowable} completes or produces an error, the current buffer is emitted, and the event is
 * propagated to all subscribed {@link Observer}s.
 * <p>
 * Note that this operation only produces <strong>non-overlapping chunks</strong>. At all times there is
 * exactly one buffer actively storing values.
 * </p>
 * 
 * @param <T> the buffered value type
 */

public final class OperatorBufferWithSingleFlowable<T, TClosing> implements Operator<List<T>, T> {
    final Supplier<? extends Flowable<? extends TClosing>> bufferClosingSelector;
    final int initialCapacity;
    /**
     * @param bufferClosingSelector
     *            a {@link Supplier} object which produces {@link Flowable}s. These {@link Flowable}s determine
     *            when a buffer is emitted and replaced by simply producing an object.
     * @param initialCapacity the initial capacity of each buffer
     */
    public OperatorBufferWithSingleFlowable(Supplier<? extends Flowable<? extends TClosing>> bufferClosingSelector,
            int initialCapacity) {
        this.bufferClosingSelector = bufferClosingSelector;
        this.initialCapacity = initialCapacity;
    }
    /**
     * @param bufferClosing
     *            An {@link Flowable} to determine
     *            when a buffer is emitted and replaced by simply producing an object.
     * @param initialCapacity the initial capacity of each buffer
     */
    public OperatorBufferWithSingleFlowable(final Flowable<? extends TClosing> bufferClosing,
            int initialCapacity) {
        this.bufferClosingSelector = new Supplier<Flowable<? extends TClosing>>() {
            @Override
            public Flowable<? extends TClosing> call() {
                return bufferClosing;
            }
        };
        this.initialCapacity = initialCapacity;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super List<T>> child) {
        Flowable<? extends TClosing> closing;
        try {
            closing = bufferClosingSelector.call();
        } catch (Throwable t) {
            child.onError(t);
            return Subscribers.empty();
        }
        final BufferingSubscriber bsub = new BufferingSubscriber(new SerializedSubscriber<List<T>>(child));

        Subscriber<TClosing> closingSubscriber = new Subscriber<TClosing>() {

            @Override
            public void onNext(TClosing t) {
                bsub.emit();
            }

            @Override
            public void onError(Throwable e) {
                bsub.onError(e);
            }

            @Override
            public void onComplete() {
                bsub.onComplete();
            }
        };

        child.add(closingSubscriber);
        child.add(bsub);
        
        closing.unsafeSubscribe(closingSubscriber);
        
        return bsub;
    }
    
    final class BufferingSubscriber extends Subscriber<T> {
        final Subscriber<? super List<T>> child;
        /** Guarded by this. */
        List<T> chunk;
        /** Guarded by this. */
        boolean done;
        public BufferingSubscriber(Subscriber<? super List<T>> child) {
            this.child = child;
            this.chunk = new ArrayList<T>(initialCapacity);
        }
        @Override
        public void onNext(T t) {
            synchronized (this) {
                if (done) {
                    return;
                }
                chunk.add(t);
            }
        }

        @Override
        public void onError(Throwable e) {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                chunk = null;
            }
            child.onError(e);
            unsubscribe();
        }

        @Override
        public void onComplete() {
            try {
                List<T> toEmit;
                synchronized (this) {
                    if (done) {
                        return;
                    }
                    done = true;
                    toEmit = chunk;
                    chunk = null;
                }
                child.onNext(toEmit);
            } catch (Throwable t) {
                child.onError(t);
                return;
            }
            child.onComplete();
            unsubscribe();
        }
        
        void emit() {
            List<T> toEmit;
            synchronized (this) {
                if (done) {
                    return;
                }
                toEmit = chunk;
                chunk = new ArrayList<T>(initialCapacity);
            }
            try {
                child.onNext(toEmit);
            } catch (Throwable t) {
                unsubscribe();
                synchronized (this) {
                    if (done) {
                        return;
                    }
                    done = true;
                }
                child.onError(t);
            }
        }
    }
    
}
