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

import java.util.*;
import java.util.concurrent.*;

import rxjf.*;
import rxjf.disposables.Disposable;
import rxjf.exceptions.Exceptions;
import rxjf.subscribers.AbstractSubscriber;

/**
 * Returns an Iterator that iterates over all items emitted by a specified Flowable.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/B.toIterator.png" alt="">
 * <p>
 * 
 * @see <a href="https://github.com/ReactiveX/RxJava/issues/50">Issue #50</a>
 */
public final class BlockingOperatorToIterator {
    private BlockingOperatorToIterator() {
        throw new IllegalStateException("No instances!");
    }

    /**
     * Returns an iterator that iterates all values of the observable.
     * 
     * @param <T>
     *            the type of source.
     * @return the iterator that could be used to iterate over the elements of the observable.
     */
    public static <T> Iterator<T> toIterator(Flowable<? extends T> source) {
        final BlockingQueue<Notification<? extends T>> notifications = new LinkedBlockingQueue<>();

        // using subscribe instead of unsafeSubscribe since this is a BlockingFlowable "final subscribe"
        final Disposable disposable = source.materialize().subscribeDisposable(new AbstractSubscriber<Notification<? extends T>>() {
            @Override
            public void onComplete() {
                // ignore
            }

            @Override
            public void onError(Throwable e) {
                notifications.offer(Notification.<T>createOnError(e));
            }

            @Override
            public void onNext(Notification<? extends T> args) {
                notifications.offer(args);
            }
        });

        return new Iterator<T>() {
            private Notification<? extends T> buf;

            @Override
            public boolean hasNext() {
                if (buf == null) {
                    buf = take();
                }
                if (buf.isOnError()) {
                    throw Exceptions.propagate(buf.getThrowable());
                }
                return !buf.isOnCompleted();
            }

            @Override
            public T next() {
                if (hasNext()) {
                    T result = buf.getValue();
                    buf = null;
                    return result;
                }
                throw new NoSuchElementException();
            }

            private Notification<? extends T> take() {
                try {
                    return notifications.take();
                } catch (InterruptedException e) {
                    disposable.dispose();
                    throw Exceptions.propagate(e);
                }
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("Read-only iterator");
            }
        };
    }

}
