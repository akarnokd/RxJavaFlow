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

import java.util.concurrent.*;

import rxjf.Flowable;
import rxjf.disposables.Disposable;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Returns a Future representing the single value emitted by an Flowable.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/B.toFuture.png" alt="">
 * <p>
 * The toFuture operation throws an exception if the Flowable emits more than one item. If the
 * Flowable may emit more than item, use <code>toList().toFuture()</code>.
 */
public final class BlockingOperatorToFuture {
    private BlockingOperatorToFuture() {
        throw new IllegalStateException("No instances!");
    }
    /**
     * Returns a Future that expects a single item from the observable.
     * 
     * @param that
     *            an observable sequence to get a Future for.
     * @param <T>
     *            the type of source.
     * @return the Future to retrieve a single elements from an Flowable
     */
    public static <T> Future<T> toFuture(Flowable<? extends T> that) {

        CompletableFuture<T> future = new CompletableFuture<>();

        Disposable d = that.single().subscribeDisposable(new AbstractSubscriber<T>() {
            T value;
            @Override
            public void onComplete() {
                future.complete(value);
            }

            @Override
            public void onError(Throwable e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onNext(T v) {
                value = v;
            }
        });
        
        return new Future<T>() {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                d.dispose();
                return future.cancel(mayInterruptIfRunning);
            }

            @Override
            public boolean isCancelled() {
                return future.isCancelled();
            }

            @Override
            public boolean isDone() {
                return future.isDone();
            }

            @Override
            public T get() throws InterruptedException, ExecutionException {
                return future.get();
            }

            @Override
            public T get(long timeout, TimeUnit unit)
                    throws InterruptedException, ExecutionException,
                    TimeoutException {
                return future.get(timeout, unit);
            }
            
        };
    }

}
