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

import rxjf.Flow.Subscriber;
import rxjf.internal.operators.OnSubscribe;
import rxjf.schedulers.Scheduler;

/**
 * An {@link Flowable} that has been grouped by key, the value of which can be obtained with {@link #getKey()}.
 * <p>
 * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
 * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
 * {@code GroupedFlowable}s that do not concern you. Instead, you can signal to them that they
 * may discard their buffers by applying an operator like {@link Flowable#take take}{@code (0)} to them.
 * 
 * @param <K>
 *            the type of the key
 * @param <T>
 *            the type of the items emitted by the {@code GroupedFlowable}
 * @see Flowable#groupBy(Func1)
 * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX documentation: GroupBy</a>
 */
public class GroupedFlowable<K, T> extends Flowable<T> {
    private final K key;

    /**
     * Converts an {@link Flowable} into a {@code GroupedFlowable} with a particular key.
     *
     * @param key
     *            the key to identify the group of items emitted by this {@code GroupedFlowable}
     * @param o
     *            the {@link Flowable} to convert
     * @return a {@code GroupedFlowable} representation of {@code o}, with key {@code key}
     */
    public static <K, T> GroupedFlowable<K, T> from(K key, final Flowable<T> o) {
        return new GroupedFlowable<>(key, s -> o.unsafeSubscribe(s));
    }

    /**
     * Returns an Flowable that will execute the specified function when a {@link Subscriber} subscribes to
     * it.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/create.png" alt="">
     * <p>
     * Write the function you pass to {@code create} so that it behaves as an Flowable: It should invoke the
     * Subscriber's {@link Subscriber#onNext onNext}, {@link Subscriber#onError onError}, and {@link Subscriber#onCompleted onCompleted} methods appropriately.
     * <p>
     * A well-formed Flowable must invoke either the Subscriber's {@code onCompleted} method exactly once or
     * its {@code onError} method exactly once.
     * <p>
     * See <a href="http://go.microsoft.com/fwlink/?LinkID=205219">Rx Design Guidelines (PDF)</a> for detailed
     * information.
     * <dl>
     * <dt><b>Scheduler:</b></dt>
     * <dd>{@code create} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <K>
     *            the type of the key
     * @param <T>
     *            the type of the items that this Flowable emits
     * @param f
     *            a function that accepts an {@code Subscriber<T>}, and invokes its {@code onNext}, {@code onError}, and {@code onCompleted} methods as appropriate
     * @return a GroupedFlowable that, when a {@link Subscriber} subscribes to it, will execute the specified
     *         function
     */
    public final static <K, T> GroupedFlowable<K, T> create(K key, OnSubscribe<T> f) {
        return new GroupedFlowable<>(key, f);
    }

    protected GroupedFlowable(K key, OnSubscribe<T> onSubscribe) {
        super(onSubscribe);
        this.key = key;
    }

    /**
     * Returns the key that identifies the group of items emited by this {@code GroupedFlowable}
     * 
     * @return the key that the items emitted by this {@code GroupedFlowable} were grouped by
     */
    public K getKey() {
        return key;
    }
}
