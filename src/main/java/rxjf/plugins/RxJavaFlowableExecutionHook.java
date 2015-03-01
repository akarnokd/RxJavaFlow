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
package rxjf.plugins;

import java.util.function.*;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.Flowable.OnSubscribe;
import rxjf.Flowable.Operator;
import rxjf.disposables.Disposable;

/**
 * Abstract ExecutionHook with invocations at different lifecycle points of {@link Flowable} execution with a
 * default no-op implementation.
 * <p>
 * See {@link RxJavaFlowPlugins} or the RxJava GitHub Wiki for information on configuring plugins:
 * <a href="https://github.com/ReactiveX/RxJava/wiki/Plugins">https://github.com/ReactiveX/RxJava/wiki/Plugins</a>.
 * <p>
 * <b>Note on thread-safety and performance:</b>
 * <p>
 * A single implementation of this class will be used globally so methods on this class will be invoked
 * concurrently from multiple threads so all functionality must be thread-safe.
 * <p>
 * Methods are also invoked synchronously and will add to execution time of the observable so all behavior
 * should be fast. If anything time-consuming is to be done it should be spawned asynchronously onto separate
 * worker threads.
 * 
 */
public abstract class RxJavaFlowableExecutionHook {
    /**
     * Invoked during the construction by {@link Flowable#create(OnSubscribe)}
     * <p>
     * This can be used to decorate or replace the <code>onSubscribe</code> function or just perform extra
     * logging, metrics and other such things and pass-thru the function.
     * 
     * @param f
     *            original {@link OnSubscribe}<{@code T}> to be executed
     * @return {@link OnSubscribe}<{@code T}> function that can be modified, decorated, replaced or just
     *         returned as a pass-thru
     */
    public <T> Consumer<Subscriber<? super T>> onCreate(Consumer<Subscriber<? super T>> f) {
        return f;
    }

    /**
     * Invoked before {@link Flowable#subscribe(rx.Subscriber)} is about to be executed.
     * <p>
     * This can be used to decorate or replace the <code>onSubscribe</code> function or just perform extra
     * logging, metrics and other such things and pass-thru the function.
     * 
     * @param onSubscribe
     *            original {@link OnSubscribe}<{@code T}> to be executed
     * @return {@link OnSubscribe}<{@code T}> function that can be modified, decorated, replaced or just
     *         returned as a pass-thru
     */
    public <T> Consumer<Subscriber<? super T>> onSubscribeStart(Flowable<? extends T> observableInstance, final Consumer<Subscriber<? super T>> onSubscribe) {
        // pass-thru by default
        return onSubscribe;
    }

    /**
     * Invoked after successful execution of {@link Flowable#subscribe(rx.Subscriber)} with returned
     * {@link Subscription}.
     * <p>
     * This can be used to decorate or replace the {@link Subscription} instance or just perform extra logging,
     * metrics and other such things and pass-thru the subscription.
     * 
     * @param subscription
     *            original {@link Subscription}
     * @return {@link Subscription} subscription that can be modified, decorated, replaced or just returned as a
     *         pass-thru
     */
    public Disposable onSubscribeReturn(Disposable subscription) {
        // pass-thru by default
        return subscription;
    }

    /**
     * Invoked after failed execution of {@link Flowable#subscribe(Subscriber)} with thrown Throwable.
     * <p>
     * This is <em>not</em> errors emitted via {@link Subscriber#onError(Throwable)} but exceptions thrown when
     * attempting to subscribe to a {@link Func1}<{@link Subscriber}{@code <T>}, {@link Subscription}>.
     * 
     * @param e
     *            Throwable thrown by {@link Flowable#subscribe(Subscriber)}
     * @return Throwable that can be decorated, replaced or just returned as a pass-thru
     */
    public <T> Throwable onSubscribeError(Throwable e) {
        // pass-thru by default
        return e;
    }

    /**
     * Invoked just as the operator functions is called to bind two operations together into a new
     * {@link Flowable} and the return value is used as the lifted function
     * <p>
     * This can be used to decorate or replace the {@link Operator} instance or just perform extra
     * logging, metrics and other such things and pass-thru the onSubscribe.
     * 
     * @param lift
     *            original {@link Operator}{@code <R, T>}
     * @return {@link Operator}{@code <R, T>} function that can be modified, decorated, replaced or just
     *         returned as a pass-thru
     */
    public <T, R> Function<Subscriber<? super R>, Subscriber<? super T>> onLift(final Function<Subscriber<? super R>, Subscriber<? super T>> lift) {
        return lift;
    }
}
