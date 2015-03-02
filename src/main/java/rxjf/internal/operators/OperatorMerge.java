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

import static rxjf.internal.UnsafeAccess.*;

import java.util.Queue;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.Flowable.Operator;
import rxjf.disposables.CompositeDisposable;
import rxjf.internal.Conformance;
import rxjf.internal.queues.*;
import rxjf.subscribers.AbstractSubscriber;

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
        return new MergeSubscriber<>(child, delayErrors, maxConcurrent);
    }
    static final class MergeSubscriber<T> extends AbstractSubscriber<Flowable<? extends T>> implements Subscription {
        final boolean delayErrors;
        final int maxConcurrent;
        final Subscriber<? super T> actual;
        final Queue<Throwable> errors;
        final CompositeDisposable composite;
        
        Subscription subscription;
        
        volatile int wip;
        static final long WIP = addressOf(MergeSubscriber.class, "wip");
        
        /** Indicates the main source has completed. */
        volatile boolean done;
        
        /** Guarded by this. */
        boolean emitting;
        
        volatile Queue<T> scalarQueue;
        static final long SCALAR_QUEUE = addressOf(MergeSubscriber.class, "scalarQueue");
        
        volatile long requested;
        static final long REQUESTED = addressOf(MergeSubscriber.class, "requested");
        
        public MergeSubscriber(Subscriber<? super T> actual, boolean delayErrors, int maxConcurrent) {
            this.actual = actual;
            this.delayErrors = delayErrors;
            this.maxConcurrent = maxConcurrent;
            this.errors = new MpscLinkedQueue<>();
            this.composite = new CompositeDisposable();
        }
        @Override
        public void onSubscribe() {
            actual.onSubscribe(this);
            if (maxConcurrent == Integer.MAX_VALUE) {
                subscription.request(Long.MAX_VALUE);
            } else {
                subscription.request(maxConcurrent);
            }
        }
        @Override
        public void onNext(Flowable<? extends T> item) {
            Conformance.itemNonNull(item);
            Conformance.subscriptionNonNull(subscription);
            
            if (item instanceof ScalarSynchronousFlow) {
                getScalarQueue().offer(((ScalarSynchronousFlow<? extends T>)item).get());
            } else {
                UNSAFE.getAndAddInt(this, WIP, 1);
                
                item.unsafeSubscribe(new InnerSubscriber());
            }
            
            drain();
        }
        
        Queue<T> getScalarQueue() {
            Queue<T> q = scalarQueue;
            if (q == null) {
                q = new SpscLinkedQueue<>();
                UNSAFE.putOrderedObject(this, SCALAR_QUEUE, q);
            }
            return q;
        }
        
        @Override
        public void onError(Throwable throwable) {
            Conformance.throwableNonNull(throwable);
            Conformance.subscriptionNonNull(subscription);
            errors.offer(throwable);
            done = true;
            
            drain();
        }
        @Override
        public void onComplete() {
            Conformance.subscriptionNonNull(subscription);
            done = true;
            drain();
        }
        
        @Override
        public void request(long n) {
            // TODO Auto-generated method stub
            
        }
        
        @Override
        public void cancel() {
            // TODO Auto-generated method stub
            
        }
        
        void drain() {
            // TODO
        }
        
        final class InnerSubscriber extends AbstractSubscriber<T> {
            final SpscArrayQueue<T> queue = new SpscArrayQueue<>(Flow.defaultBufferSize());
            @Override
            protected void onSubscribe() {
                subscription.request(Flow.defaultBufferSize());
            }
            @Override
            public void onNext(T item) {
                Conformance.itemNonNull(item);
                if (!queue.offer(item)) {
                    Conformance.mustRequestFirst(this);
                }
            }
            @Override
            public void onError(Throwable throwable) {
                MergeSubscriber.this.onError(throwable);
            }
            @Override
            public void onComplete() {
                // TODO Auto-generated method stub
            }
        }
    }
}
