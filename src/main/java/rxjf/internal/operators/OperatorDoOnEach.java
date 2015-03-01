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

import java.util.function.Consumer;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.Operator;
import rxjf.exceptions.*;
import rxjf.subscribers.AbstractSubscriber;

/**
 * 
 */
public final class OperatorDoOnEach<T> implements Operator<T, T> {
    final Consumer<? super T> onNext;
    final Consumer<Throwable> onError;
    final Runnable onComplete;
    public OperatorDoOnEach(Consumer<? super T> onNext,
            Consumer<Throwable> onError, Runnable onComplete) {
        this.onNext = onNext;
        this.onError = onError;
        this.onComplete = onComplete;
    }
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {
        return new AbstractSubscriber<T>() {
            private boolean done = false;
            @Override
            protected void onSubscribe() {
                child.onSubscribe(subscription);
            }

            @Override
            public void onComplete() {
                if (done) {
                    return;
                }
                try {
                    onComplete.run();
                } catch (Throwable e) {
                    onError(e);
                    return;
                }
                // Set `done` here so that the error in `doOnEachObserver.onCompleted()` can be noticed by observer
                done = true;
                child.onComplete();
            }

            @Override
            public void onError(Throwable e) {
                // need to throwIfFatal since we swallow errors after terminated
                Exceptions.throwIfFatal(e);
                if (done) {
                    return;
                }
                done = true;
                try {
                    onError.accept(e);
                } catch (Throwable e2) {
                    child.onError(e2);
                    return;
                }
                child.onError(e);
            }

            @Override
            public void onNext(T value) {
                if (done) {
                    return;
                }
                try {
                    onNext.accept(value);
                } catch (Throwable e) {
                    onError(OnErrorThrowable.addValueAsLastCause(e, value));
                    return;
                }
                child.onNext(value);
            }
        };
    }
}
