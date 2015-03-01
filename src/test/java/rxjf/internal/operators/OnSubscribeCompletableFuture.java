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

import java.util.concurrent.*;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.OnSubscribe;
import rxjf.internal.AbstractSubscription;

import static rxjf.internal.UnsafeAccess.*;

/**
 * 
 */
public final class OnSubscribeCompletableFuture<T> implements OnSubscribe<T> {
    final CompletableFuture<? extends T> future;
    public OnSubscribeCompletableFuture(CompletableFuture<? extends T> future) {
        this.future = future;
    }
    @Override
    public void accept(Subscriber<? super T> child) {
        // FIXME: the RS spec allows delivering onError without request which would require the subscription to be a single item queue-drain 
        child.onSubscribe(new CompletableFutureSubscription<>(child, future));
    }
    /**
     * 
     */
    private static final class CompletableFutureSubscription<T> extends
            AbstractSubscription<T> {
        final Subscriber<? super T> child;
        final CompletableFuture<? extends T> future;
        volatile CompletableFuture<? extends T> stage;
        static final long STAGE = addressOf(CompletableFutureSubscription.class, "stage");
        static final CompletableFuture<Object> CANCELLED = CompletableFuture.completedFuture(null);
        
        /**
         * @param subscriber
         */
        private CompletableFutureSubscription(Subscriber<? super T> subscriber, CompletableFuture<? extends T> future) {
            super(subscriber);
            this.child = subscriber;
            this.future = future;
        }

        @Override
        protected void onRequested(long n) {
            if (future.isDone()) {
                if (!future.isCancelled()) {
                    T value;
                    try {
                        value = future.getNow(null);
                    } catch (CompletionException t) {
                        if (t.getCause() != null) {
                            child.onError(t.getCause());
                        } else {
                            child.onError(t);
                        }
                        return;
                    }
                    child.onNext(value);
                    child.onComplete();
                }
            } else {
                CompletableFuture<? extends T> newStage = future.whenComplete((v, e) -> {
                    if (!isDisposed() && !(e instanceof CancellationException)) {
                        if (e != null) {
                            if (e instanceof CompletionException && e.getCause() != null) {
                                child.onError(e.getCause());
                            } else {
                                child.onError(e);
                            }
                        } else
                        if (v == null) {
                            child.onError(new NullPointerException());
                        } else {
                            child.onNext(v);
                            child.onComplete();
                        }
                    }
                });
                
                for (;;) {
                    CompletableFuture<? extends T> curr = stage;
                    if (curr == CANCELLED) {
                        newStage.cancel(false);
                        return;
                    }
                    if (UNSAFE.compareAndSwapObject(this, STAGE, curr, newStage)) {
                        return;
                    }
                }
            }
        }

        @Override
        protected void onCancelled() {
            CompletableFuture<?> w = (CompletableFuture<?>)UNSAFE.getAndSetObject(this, STAGE, CANCELLED);
            if (w != null) {
                w.cancel(false);
            }
        }
    }
}
