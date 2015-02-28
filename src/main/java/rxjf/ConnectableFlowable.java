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
package rxjf;

import java.util.function.Consumer;

import rxjf.Flow.Subscriber;
import rxjf.cancellables.Cancellable;
import rxjf.internal.operators.*;

/**
 * A {@code ConnectableFlowable} resembles an ordinary {@link Flowable}, except that it does not begin
 * emitting items when it is subscribed to, but only when its {@link #connect} method is called. In this way you
 * can wait for all intended {@link Subscriber}s to {@link Flowable#subscribe} to the {@code Flowable}
 * before the {@code Flowable} begins emitting items.
 * <p>
 * <img width="640" height="510" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/publishConnect.png" alt="">
 * 
 * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Connectable-Flowable-Operators">RxJava Wiki:
 *      Connectable Flowable Operators</a>
 * @param <T>
 *          the type of items emitted by the {@code ConnectableFlowable}
 */
public abstract class ConnectableFlowable<T> extends Flowable<T> {

    protected ConnectableFlowable(OnSubscribe<T> onSubscribe) {
        super(onSubscribe);
    }

    /**
     * Instructs the {@code ConnectableFlowable} to begin emitting the items from its underlying
     * {@link Flowable} to its {@link Subscriber}s.
     * <p>
     * To disconnect from a synchronous source, use the {@link #connect(rx.functions.Action1)} method.
     *
     * @return the subscription representing the connection
     * @see <a href="http://reactivex.io/documentation/operators/connect.html">ReactiveX documentation: Connect</a>
     */
    public final Cancellable connect() {
        final Cancellable[] out = new Cancellable[1];
        connect(t1 -> out[0] = t1);
        return out[0];
    }
    /**
     * Instructs the {@code ConnectableFlowable} to begin emitting the items from its underlying
     * {@link Flowable} to its {@link Subscriber}s.
     *
     * @param connection
     *          the action that receives the connection subscription before the subscription to source happens
     *          allowing the caller to synchronously disconnect a synchronous source
     * @see <a href="http://reactivex.io/documentation/operators/connect.html">ReactiveX documentation: Connect</a>
     */
    public abstract void connect(Consumer<? super Cancellable> connection);

    /**
     * Returns an {@code Flowable} that stays connected to this {@code ConnectableFlowable} as long as there
     * is at least one subscription to this {@code ConnectableFlowable}.
     * 
     * @return a {@link Flowable}
     * @see <a href="http://reactivex.io/documentation/operators/refcount.html">ReactiveX documentation: RefCount</a>
     */
    public Flowable<T> refCount() {
        return create(new OnSubscribeRefCount<>(this));
    }
}
