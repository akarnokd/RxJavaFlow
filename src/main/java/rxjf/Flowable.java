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
package rxjf;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import javax.security.auth.Subject;

import rxjf.Flow.Publisher;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.Flowable.OnSubscribe;
import rxjf.Flowable.Operator;
import rxjf.annotations.*;
import rxjf.disposables.Disposable;
import rxjf.exceptions.*;
import rxjf.internal.*;
import rxjf.internal.operators.*;
import rxjf.plugins.*;
import rxjf.schedulers.*;
import rxjf.subscribers.*;
import sun.security.pkcs11.wrapper.Functions;

/**
 *
 */
public class Flowable<T> implements Publisher<T> {
    private static final RxJavaFlowableExecutionHook hook = RxJavaFlowPlugins.getInstance().getFlowableExecutionHook();

    final OnSubscribe<T> onSubscribe;
    
    /**
     * Creates an Flowable with a Function to execute when it is subscribed to.
     * <p>
     * <em>Note:</em> Use {@link #create(OnSubscribe)} to create an Flowable, instead of this constructor,
     * unless you specifically have a need for inheritance.
     * 
     * @param f
     *            {@link OnSubscribe} to be executed when {@link #subscribe(Subscriber)} is called
     */
    protected Flowable(OnSubscribe<T> onSubscribe) {
        this.onSubscribe = hook.onCreate(Objects.requireNonNull(onSubscribe));
    }
    /**
     * Invoked when Obserable.subscribe is called.
     */
    public static interface OnSubscribe<T> extends Consumer<Subscriber<? super T>> {
        // cover for generics insanity
        @Override
        void accept(Subscriber<? super T> child);
    }

    /**
     * Operator function for lifting into an Flowable.
     */
    public interface Operator<R, T> extends Function<Subscriber<? super R>, Subscriber<? super T>> {
        // cover for generics insanity
        @Override
        Subscriber<? super T> apply(Subscriber<? super R> child);
    }
    /**
     * Transformer function used by {@link #compose}.
     * @warn more complete description needed
     */
    public static interface Transformer<T, R> extends Function<Flowable<T>, Flowable<R>> {
        // cover for generics insanity
    }
    
    /**
     * Returns an Flowable that will execute the specified function when a {@link Subscriber} subscribes to
     * it.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/create.png" alt="">
     * <p>
     * Write the function you pass to {@code create} so that it behaves as an Flowable: It should invoke the
     * Subscriber's {@link Subscriber#onNext onNext}, {@link Subscriber#onError onError}, and
     * {@link Subscriber#onCompleted onCompleted} methods appropriately.
     * <p>
     * A well-formed Flowable must invoke either the Subscriber's {@code onCompleted} method exactly once or
     * its {@code onError} method exactly once.
     * <p>
     * See <a href="http://go.microsoft.com/fwlink/?LinkID=205219">Rx Design Guidelines (PDF)</a> for detailed
     * information.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code create} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T>
     *            the type of the items that this Flowable emits
     * @param f
     *            a function that accepts an {@code Subscriber<T>}, and invokes its {@code onNext},
     *            {@code onError}, and {@code onCompleted} methods as appropriate
     * @return an Flowable that, when a {@link Subscriber} subscribes to it, will execute the specified
     *         function
     * @see <a href="http://reactivex.io/documentation/operators/create.html">ReactiveX operators documentation: Create</a>
     */
    public static <T> Flowable<T> create(OnSubscribe<T> onSubscribe) {
        return new Flowable<>(onSubscribe);
    }
    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        unsafeSubscribe(SafeSubscriber.wrap(subscriber));
    }
    /**
     * Subscribes to an Flowable and invokes {@link OnSubscribe} function without any contract protection,
     * error handling, unsubscribe, or execution hooks.
     * <p>
     * Use this only for implementing an {@link Operator} that requires nested subscriptions. For other
     * purposes, use {@link #subscribe(Subscriber)} which ensures the Rx contract and other functionality.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code unsafeSubscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param subscriber
     *              the Subscriber that will handle emissions and notifications from the Flowable
     * @return a {@link Subscription} reference with which the {@link Subscriber} can stop receiving items
     *         before the Flowable has completed
     */
    public final void unsafeSubscribe(Subscriber<? super T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        try {
            onSubscribe.accept(subscriber);
        } catch (Throwable t) {
            try {
                // FIXME We can't be sure the subscription was set or not at this point!
                subscriber.onError(t);
            } catch (Throwable t2) {
                handleUncaught(t2);
            }
        }
    }
    static void handleUncaught(Throwable t) {
        Thread currentThread = Thread.currentThread();
        currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, t);
    }
    /**
     * Lifts a function to the current Flowable and returns a new Flowable that when subscribed to will pass
     * the values of the current Flowable through the Operator function.
     * <p>
     * In other words, this allows chaining Subscribers together on an Flowable for acting on the values within
     * the Flowable.
     * <p> {@code
     * observable.map(...).filter(...).take(5).lift(new OperatorA()).lift(new OperatorB(...)).subscribe()
     * }
     * <p>
     * If the operator you are creating is designed to act on the individual items emitted by a source
     * Flowable, use {@code lift}. If your operator is designed to transform the source Flowable as a whole
     * (for instance, by applying a particular set of existing RxJava operators to it) use {@link #compose}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code lift} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param lift the Operator that implements the Flowable-operating function to be applied to the source
     *             Flowable
     * @return an Flowable that is the result of applying the lifted Operator to the source Flowable
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Implementing-Your-Own-Operators">RxJava wiki: Implementing Your Own Operators</a>
     */
    public final <R> Flowable<R> lift(Operator<? extends R, ? super T> lifter) {
        Objects.requireNonNull(lifter);
        return create(s -> {
            try {
                Subscriber<? super T> s2 = hook.onLift(lifter).apply(s);
                onSubscribe.accept(s2);
            } catch (Throwable e) {
                try {
                    // FIXME We can't be sure the subscription was set or not at this point!
                    s.onError(e);
                } catch (Throwable e2) {
                    handleUncaught(new CompositeException(Arrays.asList(e, e2)));
                }
            }
        });
    }

    /**
     * Transform an Flowable by applying a particular Transformer function to it.
     * <p>
     * This method operates on the Flowable itself whereas {@link #lift} operates on the Flowable's
     * Subscribers or Subscribers.
     * <p>
     * If the operator you are creating is designed to act on the individual items emitted by a source
     * Flowable, use {@link #lift}. If your operator is designed to transform the source Flowable as a whole
     * (for instance, by applying a particular set of existing RxJava operators to it) use {@code compose}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code compose} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param transformer implements the function that transforms the source Flowable
     * @return the source Flowable, transformed by the transformer function
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Implementing-Your-Own-Operators">RxJava wiki: Implementing Your Own Operators</a>
     */
    @SuppressWarnings("unchecked")
    public <R> Flowable<R> compose(Transformer<? super T, ? extends R> transformer) {
        return ((Transformer<T, R>) transformer).apply(this);
    }

    static final AbstractSubscriber<Object> EMPTY_SUBSCRIBER = new AbstractSubscriber<Object>() {
        @Override
        public void onNext(Object item) {
        }
        @Override
        public void onError(Throwable throwable) {
            handleUncaught(throwable);
        }
        @Override
        public void onComplete() {
        }
    }.toChecked();
    
    /**
     * Subscribes to an Flowable but ignore its emissions and notifications.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link Subscription} reference with which the {@link Subscriber} can stop receiving items before
     *         the Flowable has finished sending them
     * @throws OnErrorNotImplementedException
     *             if the Flowable tries to call {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final Disposable subscribe() {
        AbstractDisposableSubscriber<Object> cs = EMPTY_SUBSCRIBER.toDisposable();
        subscribe(cs);
        return cs;
    }

    /**
     * Subscribes to an Flowable and provides a callback to handle the items it emits.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Consumer<T>} you have designed to accept emissions from the Flowable
     * @return a {@link Subscription} reference with which the {@link Subscriber} can stop receiving items before
     *         the Flowable has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null
     * @throws OnErrorNotImplementedException
     *             if the Flowable tries to call {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final Disposable subscribe(Consumer<? super T> onNext) {
        Objects.requireNonNull(onNext);
        AbstractDisposableSubscriber<T> cs = new AbstractSubscriber<T>() {
            @Override
            public void onNext(T item) {
                onNext.accept(item);
            }
            @Override
            public void onError(Throwable throwable) {
                handleUncaught(throwable);
            }
            @Override
            public void onComplete() {
            }
        }.toDisposable();
        subscribe(cs);
        return cs;
    }

    /**
     * Subscribes to an Flowable and provides callbacks to handle the items it emits and any error
     * notification it issues.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Consumer<T>} you have designed to accept emissions from the Flowable
     * @param onError
     *             the {@code Consumer<Throwable>} you have designed to accept any error notification from the
     *             Flowable
     * @return a {@link Subscription} reference with which the {@link Subscriber} can stop receiving items before
     *         the Flowable has finished sending them
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null
     */
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<Throwable> onError) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        AbstractDisposableSubscriber<T> cs = new AbstractSubscriber<T>() {
            @Override
            public void onNext(T item) {
                onNext.accept(item);
            }
            @Override
            public void onError(Throwable throwable) {
                onError.accept(throwable);
            }
            @Override
            public void onComplete() {
            }
        }.toDisposable();
        subscribe(cs);
        return cs;
    }

    /**
     * Subscribes to an Flowable and provides callbacks to handle the items it emits and any error or
     * completion notification it issues.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Consumer<T>} you have designed to accept emissions from the Flowable
     * @param onError
     *             the {@code Consumer<Throwable>} you have designed to accept any error notification from the
     *             Flowable
     * @param onComplete
     *             the {@code Runnable} you have designed to accept a completion notification from the
     *             Flowable
     * @return a {@link Subscription} reference with which the {@link Subscriber} can stop receiving items before
     *         the Flowable has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        Objects.requireNonNull(onComplete);
        AbstractDisposableSubscriber<T> cs = new AbstractSubscriber<T>() {
            @Override
            public void onNext(T item) {
                onNext.accept(item);
            }
            @Override
            public void onError(Throwable throwable) {
                onError.accept(throwable);
            }
            @Override
            public void onComplete() {
                onComplete.run();
            }
        }.toDisposable();
        subscribe(cs);
        return cs;
    }
    
    // TODO javadoc
    public final Disposable subscribeDisposable(Subscriber<? super T> subscriber) {
        AbstractDisposableSubscriber<? super T> cs = DisposableSubscriber.wrap(subscriber);
        subscribe(cs);
        return cs;
    }

    // -----------------------------------------------------------
    // -  OPERATORS ----------------------------------------------
    // -----------------------------------------------------------
    // TODO javadoc
    public static <T> Flowable<T> from(Publisher<? extends T> publisher) {
        Objects.requireNonNull(publisher);
        if (publisher instanceof Flowable) {
            @SuppressWarnings("unchecked")
            Flowable<T> fo = (Flowable<T>) publisher;
            return fo;
        }
        return create(s -> publisher.subscribe(s)); // publisher::subscribe doesn't compile
    }
    /**
     * Returns an Flowable that emits a single item and then completes.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.png" alt="">
     * <p>
     * To convert any object into an Flowable that emits that object, pass that object into the {@code just}
     * method.
     * <p>
     * This is similar to the {@link #from(java.lang.Object[])} method, except that {@code from} will convert
     * an {@link Iterable} object into an Flowable that emits each of the items in the Iterable, one at a
     * time, while the {@code just} method converts an Iterable into an Flowable that emits the entire
     * Iterable as a single item.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param value
     *            the item to emit
     * @param <T>
     *            the type of that item
     * @return an Flowable that emits {@code value} as a single item and then completes
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    public static <T> Flowable<T> just(T value) {
        return ScalarSynchronousFlow.create(Conformance.itemNonNull(value));
    }
    
    /**
     * Converts an {@link Iterable} sequence into an Flowable that emits the items in the sequence.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param iterable
     *            the source {@link Iterable} sequence
     * @param <T>
     *            the type of items in the {@link Iterable} sequence and the type of items to be emitted by the
     *            resulting Flowable
     * @return an Flowable that emits each item in the source {@link Iterable} sequence
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    public static <T> Flowable<T> from(Iterable<? extends T> source) {
        return create(new OnSubscribeIterable<>(Objects.requireNonNull(source)));
    }
    /**
     * Converts an Array into an Flowable that emits the items in the Array.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param array
     *            the source Array
     * @param <T>
     *            the type of items in the Array and the type of items to be emitted by the resulting Flowable
     * @return an Flowable that emits each item in the source Array
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    @SafeVarargs
    public static <T> Flowable<T> from(T... values) {
        Objects.requireNonNull(values);
        if (values.length == 0) {
            return empty();
        } else
        if (values.length == 1) {
            return just(values[0]);
        }
        return create(new OnSubscribeArray<>(values));
    }
    
    /**
     * Returns an Flowable that applies a specified function to each item emitted by the source Flowable and
     * emits the results of these function applications.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/map.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code map} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param function
     *            a function to apply to each item emitted by the Flowable
     * @return an Flowable that emits the items from the source Flowable, transformed by the specified
     *         function
     * @see <a href="http://reactivex.io/documentation/operators/map.html">ReactiveX operators documentation: Map</a>
     */
    public final <R> Flowable<R> map(Function<? super T, ? extends R> function) {
        Objects.requireNonNull(function);
        return lift(new OperatorMap<>(function));
    }
    static final Flowable<Object> EMPTY = create(new OnSubscribeEmpty<>());
    /**
     * Returns an Flowable that emits no items to the {@link Subscriber} and immediately invokes its
     * {@link Subscriber#onCompleted onCompleted} method.
     * <p>
     * <img width="640" height="190" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/empty.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code empty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the type of the items (ostensibly) emitted by the Flowable
     * @return an Flowable that emits no items to the {@link Subscriber} but immediately invokes the
     *         {@link Subscriber}'s {@link Subscriber#onCompleted() onCompleted} method
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Empty</a>
     */
    @SuppressWarnings("unchecked")
    public static <T> Flowable<T> empty() {
        return (Flowable<T>)EMPTY;
    }
    static final Flowable<Object> NEVER = create(new OnSubscribeNever<>());
    /**
     * Returns an Flowable that never sends any items or notifications to an {@link Subscriber}.
     * <p>
     * <img width="640" height="185" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/never.png" alt="">
     * <p>
     * This Flowable is useful primarily for testing purposes.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code never} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T>
     *            the type of items (not) emitted by the Flowable
     * @return an Flowable that never emits any items or sends any notifications to an {@link Subscriber}
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Never</a>
     */
    @SuppressWarnings("unchecked")
    public static <T> Flowable<T> never() {
        return (Flowable<T>)NEVER;
    }
    
    /**
     * Returns an Flowable that emits only the first {@code num} items emitted by the source Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.png" alt="">
     * <p>
     * This method returns an Flowable that will invoke a subscribing {@link Subscriber}'s
     * {@link Subscriber#onNext onNext} function a maximum of {@code num} times before invoking
     * {@link Subscriber#onCompleted onCompleted}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code take} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param num
     *            the maximum number of items to emit
     * @return an Flowable that emits only the first {@code num} items emitted by the source Flowable, or
     *         all of the items from the source Flowable if that Flowable emits fewer than {@code num} items
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    public final Flowable<T> take(long n) {
        return lift(new OperatorTake<>(n));
    }
    /**
     * Returns an Flowable that emits a sequence of Integers within a specified range.
     * <p>
     * <img width="640" height="195" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/range.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code range} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param start
     *            the value of the first Integer in the sequence
     * @param count
     *            the number of sequential Integers to generate
     * @return an Flowable that emits a range of sequential Integers
     * @throws IllegalArgumentException
     *             if {@code count} is less than zero, or if {@code start} + {@code count} &minus; 1 exceeds
     *             {@code Integer.MAX_VALUE}
     * @see <a href="http://reactivex.io/documentation/operators/range.html">ReactiveX operators documentation: Range</a>
     */
    public static Flowable<Integer> range(int start, int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        } else
        if (start > Integer.MAX_VALUE - count + 1) {
            throw new IllegalArgumentException("start + count can not exceed Integer.MAX_VALUE");
        } else
        if (count == 0) {
            return empty();
        } else
        if (count == 1) {
            return just(start);
        }
        return create(new OnSubscribeRange(start, count));
    }
    /**
     * Returns an Flowable that emits a sequence of Longs within a specified range.
     * <p>
     * <img width="640" height="195" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/range.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code range} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param start
     *            the value of the first Long in the sequence
     * @param count
     *            the number of sequential Longs to generate
     * @return an Flowable that emits a range of sequential Longs
     * @throws IllegalArgumentException
     *             if {@code count} is less than zero, or if {@code start} + {@code count} &minus; 1 exceeds
     *             {@code Integer.MAX_VALUE}
     * @see <a href="http://reactivex.io/documentation/operators/range.html">ReactiveX operators documentation: Range</a>
     */
    public static Flowable<Long> rangeLong(long start, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must be non-negative");
        } else
        if (start > Long.MAX_VALUE - count + 1) {
            throw new IllegalArgumentException("start + count can not exceed Long.MAX_VALUE");
        } else
        if (count == 0) {
            return empty();
        } else
        if (count == 1) {
            return just(start);
        }
        return create(new OnSubscribeRangeLong(start, count));
    }
    /**
     * Returns an Flowable that mirrors the source Flowable but applies a timeout policy for each emitted
     * item. If the next item isn't emitted within the specified timeout duration starting from its predecessor,
     * the resulting Flowable terminates and notifies observers of a {@code TimeoutException}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.1.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between emitted items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument.
     * @return the source Flowable modified to notify observers of a {@code TimeoutException} in case of a
     *         timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final Flowable<T> timeout(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit);
        return timeout(timeout, unit, Schedulers.computation());
    }
    /**
     * Returns an Flowable that mirrors the source Flowable but applies a timeout policy for each emitted
     * item, where this policy is governed on a specified Scheduler. If the next item isn't emitted within the
     * specified timeout duration starting from its predecessor, the resulting Flowable terminates and
     * notifies observers of a {@code TimeoutException}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.1s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument
     * @param scheduler
     *            the Scheduler to run the timeout timers on
     * @return the source Flowable modified to notify observers of a {@code TimeoutException} in case of a
     *         timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final Flowable<T> timeout(long timeout, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(scheduler);
        // TODO
        throw new UnsupportedOperationException();
    }
    /**
     * Converts an Flowable into a {@link BlockingFlowable} (an Flowable with blocking operators).
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toBlocking} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return a {@code BlockingFlowable} version of this Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final BlockingFlowable<T> toBlocking() {
        return BlockingFlowable.from(this);
    }
    /**
     * Returns an Flowable that emits a sequential number every specified interval of time.
     * <p>
     * <img width="640" height="195" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/interval.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code interval} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param interval
     *            interval size in time units (see below)
     * @param unit
     *            time units to use for the interval size
     * @return an Flowable that emits a sequential number each time interval
     * @see <a href="http://reactivex.io/documentation/operators/interval.html">ReactiveX operators documentation: Interval</a>
     */
    public final static Flowable<Long> interval(long interval, TimeUnit unit) {
        Objects.requireNonNull(unit);
        return interval(interval, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits a sequential number every specified interval of time, on a
     * specified Scheduler.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/interval.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param interval
     *            interval size in time units (see below)
     * @param unit
     *            time units to use for the interval size
     * @param scheduler
     *            the Scheduler to use for scheduling the items
     * @return an Flowable that emits a sequential number each time interval
     * @see <a href="http://reactivex.io/documentation/operators/interval.html">ReactiveX operators documentation: Interval</a>
     */
    public final static Flowable<Long> interval(long interval, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(scheduler);
        return create(new OnSubscribeTimerPeriodically(interval, interval, unit, scheduler));
    }
    /**
     * Returns an Flowable that emits a {@code 0L} after the {@code initialDelay} and ever increasing numbers
     * after each {@code period} of time thereafter.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.p.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. If the downstream needs a slower rate
     *      it should slow the timer or use something like {@link #onBackpressureDrop}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param initialDelay
     *            the initial delay time to wait before emitting the first value of 0L
     * @param period
     *            the period of time between emissions of the subsequent numbers
     * @param unit
     *            the time unit for both {@code initialDelay} and {@code period}
     * @return an Flowable that emits a 0L after the {@code initialDelay} and ever increasing numbers after
     *         each {@code period} of time thereafter
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX operators documentation: Timer</a>
     */
    public final static Flowable<Long> timer(long initialDelay, long period, TimeUnit unit) {
        Objects.requireNonNull(unit);
        return timer(initialDelay, period, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits a {@code 0L} after the {@code initialDelay} and ever increasing numbers
     * after each {@code period} of time thereafter, on a specified {@link Scheduler}.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.ps.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. If the downstream needs a slower rate
     *      it should slow the timer or use something like {@link #onBackpressureDrop}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param initialDelay
     *            the initial delay time to wait before emitting the first value of 0L
     * @param period
     *            the period of time between emissions of the subsequent numbers
     * @param unit
     *            the time unit for both {@code initialDelay} and {@code period}
     * @param scheduler
     *            the Scheduler on which the waiting happens and items are emitted
     * @return an Flowable that emits a 0L after the {@code initialDelay} and ever increasing numbers after
     *         each {@code period} of time thereafter, while running on the given Scheduler
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX operators documentation: Timer</a>
     */
    public final static Flowable<Long> timer(long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(scheduler);
        return create(new OnSubscribeTimerPeriodically(initialDelay, period, unit, scheduler));
    }

    /**
     * Returns an Flowable that emits one item after a specified delay, and then completes.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. If the downstream needs a slower rate
     *      it should slow the timer or use something like {@link #onBackpressureDrop}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param delay
     *            the initial delay before emitting a single {@code 0L}
     * @param unit
     *            time units to use for {@code delay}
     * @return an Flowable that emits one item after a specified delay, and then completes
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX operators documentation: Timer</a>
     */
    public final static Flowable<Long> timer(long delay, TimeUnit unit) {
        Objects.requireNonNull(unit);
        return timer(delay, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits one item after a specified delay, on a specified Scheduler, and then
     * completes.
     * <p>
     * <img width="640" height="200" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timer.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. If the downstream needs a slower rate
     *      it should slow the timer or use something like {@link #onBackpressureDrop}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param delay
     *            the initial delay before emitting a single 0L
     * @param unit
     *            time units to use for {@code delay}
     * @param scheduler
     *            the {@link Scheduler} to use for scheduling the item
     * @return an Flowable that emits one item after a specified delay, on a specified Scheduler, and then
     *         completes
     * @see <a href="http://reactivex.io/documentation/operators/timer.html">ReactiveX operators documentation: Timer</a>
     */
    public final static Flowable<Long> timer(long delay, TimeUnit unit, Scheduler scheduler) {
        Objects.requireNonNull(unit);
        Objects.requireNonNull(scheduler);
        return create(new OnSubscribeTimerOnce(delay, unit, scheduler));
    }
    /**
     * Modifies an Flowable to perform its emissions and notifications on a specified {@link Scheduler},
     * asynchronously with an unbounded buffer.
     * <p>
     * <img width="640" height="308" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/observeOn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to notify {@link Subscriber}s on
     * @return the source Flowable modified so that its {@link Subscriber}s are notified on the specified
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/observeon.html">ReactiveX operators documentation: ObserveOn</a>
     * @see <a href="http://www.grahamlea.com/2014/07/rxjava-threading-examples/">RxJava Threading Examples</a>
     * @see #subscribeOn
     */
    public final Flowable<T> observeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler);
        if (this instanceof ScalarSynchronousFlow) {
            return ((ScalarSynchronousFlow<T>)this).scalarScheduleOn(scheduler);
        }
        return lift(new OperatorObserveOn<T>(scheduler));
    }
    /**
     * Asynchronously subscribes Subscribers to this Flowable on the specified {@link Scheduler}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/subscribeOn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to perform subscription actions on
     * @return the source Flowable modified so that its subscriptions happen on the
     *         specified {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/subscribeon.html">ReactiveX operators documentation: SubscribeOn</a>
     * @see <a href="http://www.grahamlea.com/2014/07/rxjava-threading-examples/">RxJava Threading Examples</a>
     * @see #observeOn
     */
    public final Flowable<T> subscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler);
        if (this instanceof ScalarSynchronousFlow) {
            return ((ScalarSynchronousFlow<T>)this).scalarScheduleOn(scheduler);
        }
        return nest().lift(new OperatorSubscribeOn<T>(scheduler));
    }
    /**
     * Modifies the source Flowable so that subscribers will unsubscribe from it on a specified
     * {@link Scheduler}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to perform unsubscription actions on
     * @return the source Flowable modified so that its unsubscriptions happen on the specified
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/subscribeon.html">ReactiveX operators documentation: SubscribeOn</a>
     */
    public final Flowable<T> unsubscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler);
        return lift(new OperatorUnsubscribeOn<T>(scheduler));
    }

    /**
     * Returns an Flowable that emits items based on applying a function that you supply to each item emitted
     * by the source Flowable, where that function returns an Flowable, and then merging those resulting
     * Flowables and emitting the results of this merger.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/flatMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param func
     *            a function that, when applied to an item emitted by the source Flowable, returns an
     *            Flowable
     * @return an Flowable that emits the result of applying the transformation function to each item emitted
     *         by the source Flowable and merging the results of the Flowables obtained from this
     *         transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <R> Flowable<R> flatMap(Function<? super T, ? extends Flowable<? extends R>> func) {
        return merge(map(func));
    }
    /**
     * Returns an Flowable that emits items based on applying a function that you supply to each item emitted
     * by the source Flowable, where that function returns an Flowable, and then merging those resulting
     * Flowables and emitting the results of this merger, while limiting the maximum number of concurrent
     * subscriptions to these Flowables.
     * <p>
     * <!-- <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/flatMap.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param func
     *            a function that, when applied to an item emitted by the source Flowable, returns an
     *            Flowable
     * @param maxConcurrent
     *         the maximum number of Flowables that may be subscribed to concurrently
     * @return an Flowable that emits the result of applying the transformation function to each item emitted
     *         by the source Flowable and merging the results of the Flowables obtained from this
     *         transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    public final <R> Flowable<R> flatMap(Function<? super T, ? extends Flowable<? extends R>> func, int maxConcurrent) {
        return merge(map(func), maxConcurrent);
    }
    /**
     * Flattens an Flowable that emits Flowables into a single Flowable that emits the items emitted by
     * those Flowables, without any transformation, while limiting the maximum number of concurrent
     * subscriptions to these Flowables.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.oo.png" alt="">
     * <p>
     * You can combine the items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param source
     *            an Flowable that emits Flowables
     * @param maxConcurrent
     *            the maximum number of Flowables that may be subscribed to concurrently
     * @return an Flowable that emits items that are the result of flattening the Flowables emitted by the
     *         {@code source} Flowable
     * @throws IllegalArgumentException
     *             if {@code maxConcurrent} is less than or equal to 0
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> merge(Flowable<? extends Flowable<? extends T>> source, int maxConcurrent) {
        return source.lift(OperatorMerge.<T>instance(false, maxConcurrent));
    }
    /**
     * Flattens an Flowable that emits Flowables into a single Flowable that emits the items emitted by
     * those Flowables, without any transformation.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.oo.png" alt="">
     * <p>
     * You can combine the items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param source
     *            an Flowable that emits Flowables
     * @return an Flowable that emits items that are the result of flattening the Flowables emitted by the
     *         {@code source} Flowable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> merge(Flowable<? extends Flowable<? extends T>> source) {
        return source.lift(OperatorMerge.<T>instance(false));
    }
    
    /**
     * Flattens an Iterable of Flowables into one Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine the items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param sequences
     *            the Iterable of Flowables
     * @return an Flowable that emits items that are the result of flattening the items emitted by the
     *         Flowables in the Iterable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> merge(Iterable<? extends Flowable<? extends T>> sequences) {
        return merge(from(sequences));
    }

    /**
     * Flattens an Iterable of Flowables into one Flowable, without any transformation, while limiting the
     * number of concurrent subscriptions to these Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine the items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param sequences
     *            the Iterable of Flowables
     * @param maxConcurrent
     *            the maximum number of Flowables that may be subscribed to concurrently
     * @return an Flowable that emits items that are the result of flattening the items emitted by the
     *         Flowables in the Iterable
     * @throws IllegalArgumentException
     *             if {@code maxConcurrent} is less than or equal to 0
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> merge(Iterable<? extends Flowable<? extends T>> sequences, int maxConcurrent) {
        return merge(from(sequences), maxConcurrent);
    }
    /**
     * Flattens an Flowable that emits Flowables into one Flowable, in a way that allows an Subscriber to
     * receive all successfully emitted items from all of the source Flowables without being interrupted by
     * an error notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable)} except that if any of the merged Flowables notify of an
     * error via {@link Subscriber#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param source
     *            an Flowable that emits Flowables
     * @return an Flowable that emits all of the items emitted by the Flowables emitted by the
     *         {@code source} Flowable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends Flowable<? extends T>> source) {
        return source.lift(OperatorMerge.<T>instance(true));
    }
    /**
     * Flattens an Array of Flowables into one Flowable, without any transformation.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.io.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param sequences
     *            the Array of Flowables
     * @return an Flowable that emits all of the items emitted by the Flowables in the Array
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SafeVarargs
    public final static <T> Flowable<T> merge(Flowable<? extends T>... sequences) {
        return merge(from(sequences));
    }
    /**
     * Flattens an Array of Flowables into one Flowable, without any transformation, while limiting the
     * number of concurrent subscriptions to these Flowables.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.io.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param maxConcurrent
     *            the maximum number of Flowables that may be subscribed to concurrently
     * @param sequences
     *            the Array of Flowables
     * @return an Flowable that emits all of the items emitted by the Flowables in the Array
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SafeVarargs
    public final static <T> Flowable<T> merge(int maxConcurrent, Flowable<? extends T>... sequences) {
        return merge(from(sequences), maxConcurrent);
    }
    /**
     * Flattens an Flowable that emits Flowables into one Flowable, in a way that allows an Subscriber to
     * receive all successfully emitted items from all of the source Flowables without being interrupted by
     * an error notification from one of them, while limiting the
     * number of concurrent subscriptions to these Flowables.
     * <p>
     * This behaves like {@link #merge(Flowable)} except that if any of the merged Flowables notify of an
     * error via {@link Subscriber#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param source
     *            an Flowable that emits Flowables
     * @param maxConcurrent
     *            the maximum number of Flowables that may be subscribed to concurrently
     * @return an Flowable that emits all of the items emitted by the Flowables emitted by the
     *         {@code source} Flowable
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends Flowable<? extends T>> source, int maxConcurrent) {
        return source.lift(OperatorMerge.<T>instance(true, maxConcurrent));
    }
    /**
     * Returns an Flowable that emits a single item, a list composed of all the items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toList.png" alt="">
     * <p>
     * Normally, an Flowable that returns multiple items will do so by invoking its {@link Subscriber}'s
     * {@link Subscriber#onNext onNext} method for each such item. You can change this behavior, instructing the
     * Flowable to compose a list of all of these items and then to invoke the Subscriber's {@code onNext}
     * function once, passing it the entire list, by calling the Flowable's {@code toList} method prior to
     * calling its {@link #subscribe} method.
     * <p>
     * Be careful not to use this operator on Flowables that emit infinite or very large numbers of items, as
     * you do not have the option to unsubscribe.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits a single item: a List containing all of the items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final Flowable<List<T>> toList() {
        return lift(OperatorToList.<T>instance());
    }
    /**
     * Modifies the source Flowable so that it invokes an action when it calls {@code onCompleted}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnCompleted.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnCompleted} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onCompleted
     *            the action to invoke when the source Flowable calls {@code onCompleted}
     * @return the source Flowable with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    public final Flowable<T> doOnCompleted(final Runnable onCompleted) {
        return lift(new OperatorDoOnEach<T>(v -> { }, e -> { }, onCompleted));
    }

    /**
     * Modifies the source Flowable so that it invokes an action for each item it emits.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnEach.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNotification
     *            the action to invoke for each item emitted by the source Flowable
     * @return the source Flowable with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    public final Flowable<T> doOnEach(final Consumer<Notification<? super T>> onNotification) {
        return lift(new OperatorDoOnEach<T>(
                v -> {
                    onNotification.accept(Notification.createOnNext(v));
                },
                e -> {
                    onNotification.accept(Notification.createOnError(e));
                },
                () -> {
                    onNotification.accept(Notification.createOnCompleted());
                }
        ));
    }

    /**
     * Modifies the source Flowable so that it notifies an Subscriber for each item it emits.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnEach.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param onNext
     *            the action to invoke when the source Flowable calls {@code onNext}
     * @param onError
     *            the action to invoke if the source Flowable calls {@code onError}
     * @param onCompleted
     *            the action to invoke when the source Flowable calls {@code onCompleted}
     * @return the source Flowable with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    public final Flowable<T> doOnEach(Consumer<? super T> onNext,
            Consumer<Throwable> onError, Runnable onComplete) {
        return lift(new OperatorDoOnEach<T>(onNext, onError, onComplete));
    }

    /**
     * Modifies the source Flowable so that it invokes an action if it calls {@code onError}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnError.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onError
     *            the action to invoke if the source Flowable calls {@code onError}
     * @return the source Flowable with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    public final Flowable<T> doOnError(final Consumer<Throwable> onError) {
        return lift(new OperatorDoOnEach<T>(v -> {}, onError, () -> { }));
    }

    /**
     * Modifies the source Flowable so that it invokes an action when it calls {@code onNext}.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnNext.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            the action to invoke when the source Flowable calls {@code onNext}
     * @return the source Flowable with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    public final Flowable<T> doOnNext(final Consumer<? super T> onNext) {
        return lift(new OperatorDoOnEach<T>(onNext, e -> { }, () -> { }));
    }
    /**
     * Returns an Flowable that emits the last item emitted by the source Flowable or notifies observers of
     * a {@code NoSuchElementException} if the source Flowable is empty.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/last.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code last} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits the last item from the source Flowable or notifies observers of an
     *         error
     * @see <a href="http://reactivex.io/documentation/operators/last.html">ReactiveX operators documentation: Last</a>
     */
    public final Flowable<T> last() {
        return takeLast(1).single();
    }
    /**
     * Returns an Flowable that emits only the last {@code count} items emitted by the source Flowable.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.n.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLast} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the number of items to emit from the end of the sequence of items emitted by the source
     *            Flowable
     * @return an Flowable that emits only the last {@code count} items emitted by the source Flowable
     * @throws IndexOutOfBoundsException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<T> takeLast(final int count) {
        return lift(new OperatorTakeLast<T>(count));
    }
    /**
     * Returns an Flowable that emits the single item emitted by the source Flowable, if that Flowable
     * emits only a single item. If the source Flowable emits more than one item or no items, notify of an
     * {@code IllegalArgumentException} or {@code NoSuchElementException} respectively.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/single.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code single} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits the single item emitted by the source Flowable
     * @throws IllegalArgumentException
     *             if the source emits more than one item
     * @throws NoSuchElementException
     *             if the source emits no items
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> single() {
        return lift(new OperatorSingle<T>());
    }
    /**
     * Returns an Flowable that invokes an {@link Subscriber}'s {@link Subscriber#onError onError} method when the
     * Subscriber subscribes to it.
     * <p>
     * <img width="640" height="190" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/error.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code error} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param exception
     *            the particular Throwable to pass to {@link Subscriber#onError onError}
     * @param <T>
     *            the type of the items (ostensibly) emitted by the Flowable
     * @return an Flowable that invokes the {@link Subscriber}'s {@link Subscriber#onError onError} method when
     *         the Subscriber subscribes to it
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Throw</a>
     */
    public final static <T> Flowable<T> error(Throwable exception) {
        return create(s -> {
            s.onSubscribe(AbstractSubscription.createEmpty(s));
            s.onError(exception);
        });
    }
    /**
     * Returns an Flowable that emits the single item emitted by the source Flowable that matches a
     * specified predicate, if that Flowable emits one such item. If the source Flowable emits more than one
     * such item or no such items, notify of an {@code IllegalArgumentException} or
     * {@code NoSuchElementException} respectively.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/single.p.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code single} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a predicate function to evaluate items emitted by the source Flowable
     * @return an Flowable that emits the single item emitted by the source Flowable that matches the
     *         predicate
     * @throws IllegalArgumentException
     *             if the source Flowable emits more than one item that matches the predicate
     * @throws NoSuchElementException
     *             if the source Flowable emits no item that matches the predicate
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> single(Predicate<? super T> predicate) {
        return filter(predicate).single();
    }

    /**
     * Returns an Flowable that emits the single item emitted by the source Flowable, if that Flowable
     * emits only a single item, or a default item if the source Flowable emits no items. If the source
     * Flowable emits more than one item, throw an {@code IllegalArgumentException}.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/singleOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code singleOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            a default value to emit if the source Flowable emits no item
     * @return an Flowable that emits the single item emitted by the source Flowable, or a default item if
     *         the source Flowable is empty
     * @throws IllegalArgumentException
     *             if the source Flowable emits more than one item
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> singleOrDefault(T defaultValue) {
        return lift(new OperatorSingle<>(defaultValue));
    }

    /**
     * Returns an Flowable that emits the single item emitted by the source Flowable that matches a
     * predicate, if that Flowable emits only one such item, or a default item if the source Flowable emits
     * no such items. If the source Flowable emits more than one such item, throw an
     * {@code IllegalArgumentException}.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/singleOrDefault.p.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code singleOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            a default item to emit if the source Flowable emits no matching items
     * @param predicate
     *            a predicate function to evaluate items emitted by the source Flowable
     * @return an Flowable that emits the single item emitted by the source Flowable that matches the
     *         predicate, or the default item if no emitted item matches the predicate
     * @throws IllegalArgumentException
     *             if the source Flowable emits more than one item that matches the predicate
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> singleOrDefault(T defaultValue, Predicate<? super T> predicate) {
        return filter(predicate).singleOrDefault(defaultValue);
    }
    /**
     * Filters items emitted by an Flowable by only emitting those that satisfy a specified predicate.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/filter.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code filter} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function that evaluates each item emitted by the source Flowable, returning {@code true}
     *            if it passes the filter
     * @return an Flowable that emits only those items emitted by the source Flowable that the filter
     *         evaluates as {@code true}
     * @see <a href="http://reactivex.io/documentation/operators/filter.html">ReactiveX operators documentation: Filter</a>
     */
    public final Flowable<T> filter(Predicate<? super T> predicate) {
        return lift(new OperatorFilter<T>(predicate));
    }
    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable, then feeds the result of that function along with the second item emitted by the source
     * Flowable into the same function, and so on until all items have been emitted by the source Flowable,
     * and emits the final result from the final call to your function as its sole item.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/reduce.png" alt="">
     * <p>
     * This technique, which is called "reduce" here, is sometimes called "aggregate," "fold," "accumulate,"
     * "compress," or "inject" in other programming contexts. Groovy, for instance, has an {@code inject} method
     * that does a similar operation on lists.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because by intent it will receive all values and reduce
     *      them to a single {@code onNext}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code reduce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, whose
     *            result will be used in the next accumulator call
     * @return an Flowable that emits a single item that is the result of accumulating the items emitted by
     *         the source Flowable
     * @throws IllegalArgumentException
     *             if the source Flowable emits no items
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    public final Flowable<T> reduce(BinaryOperator<T> accumulator) {
        /*
         * Discussion and confirmation of implementation at
         * https://github.com/ReactiveX/RxJava/issues/423#issuecomment-27642532
         * 
         * It should use last() not takeLast(1) since it needs to emit an error if the sequence is empty.
         */
        return scan(accumulator).last();
    }
    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable, then feeds the result of that function along with the second item emitted by the source
     * Flowable into the same function, and so on until all items have been emitted by the source Flowable,
     * emitting the result of each of these iterations.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/scan.png" alt="">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code scan} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, whose
     *            result will be emitted to {@link Subscriber}s via {@link Subscriber#onNext onNext} and used in the
     *            next accumulator call
     * @return an Flowable that emits the results of each call to the accumulator function
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX operators documentation: Scan</a>
     */
    public final Flowable<T> scan(BinaryOperator<T> accumulator) {
        return lift(new OperatorScan<>(accumulator));
    }
    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable and a specified seed value, then feeds the result of that function along with the second item
     * emitted by an Flowable into the same function, and so on until all items have been emitted by the
     * source Flowable, emitting the final result from the final call to your function as its sole item.
     * <p>
     * <img width="640" height="325" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/reduceSeed.png" alt="">
     * <p>
     * This technique, which is called "reduce" here, is sometimec called "aggregate," "fold," "accumulate,"
     * "compress," or "inject" in other programming contexts. Groovy, for instance, has an {@code inject} method
     * that does a similar operation on lists.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because by intent it will receive all values and reduce
     *      them to a single {@code onNext}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code reduce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param initialValue
     *            the initial (seed) accumulator value
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, the
     *            result of which will be used in the next accumulator call
     * @return an Flowable that emits a single item that is the result of accumulating the output from the
     *         items emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    public final <R> Flowable<R> reduce(R initialValue, BiFunction<R, ? super T, R> accumulator) {
        return scan(initialValue, accumulator).takeLast(1);
    }
    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable and a seed value, then feeds the result of that function along with the second item emitted by
     * the source Flowable into the same function, and so on until all items have been emitted by the source
     * Flowable, emitting the result of each of these iterations.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/scanSeed.png" alt="">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <p>
     * Note that the Flowable that results from this method will emit {@code initialValue} as its first
     * emitted item.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code scan} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param initialValue
     *            the initial (seed) accumulator item
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, whose
     *            result will be emitted to {@link Subscriber}s via {@link Subscriber#onNext onNext} and used in the
     *            next accumulator call
     * @return an Flowable that emits {@code initialValue} followed by the results of each call to the
     *         accumulator function
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX operators documentation: Scan</a>
     */
    public final <R> Flowable<R> scan(R initialValue, BiFunction<R, ? super T, R> accumulator) {
        return lift(new OperatorScan<R, T>(initialValue, accumulator));
    }
    /**
     * Returns an Flowable that emits the count of the total number of items emitted by the source Flowable.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/count.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because by intent it will receive all values and reduce
     *      them to a single {@code onNext}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code count} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits a single item: the number of elements emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/count.html">ReactiveX operators documentation: Count</a>
     * @see #countLong()
     */
    public final Flowable<Integer> count() {
        return reduce(0, (t1, t2) -> t1 + 1);
    }
    // TODO javadoc
    public static <T> Flowable<T> from(CompletableFuture<T> future) {
        return create(new OnSubscribeCompletableFuture<>(future));
    }
    /**
     * Mirrors the one Flowable in an Iterable of several Flowables that first either emits an item or sends
     * a termination notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param sources
     *            an Iterable of Flowable sources competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Iterable<? extends Flowable<? extends T>> sources) {
        return create(OnSubscribeAmb.amb(sources));
    }

    /**
     * Given two Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2) {
        return create(OnSubscribeAmb.amb(o1, o2));
    }

    /**
     * Given three Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @param o3
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2, Flowable<? extends T> o3) {
        return create(OnSubscribeAmb.amb(o1, o2, o3));
    }

    /**
     * Given four Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @param o3
     *            an Flowable competing to react first
     * @param o4
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2, Flowable<? extends T> o3, Flowable<? extends T> o4) {
        return create(OnSubscribeAmb.amb(o1, o2, o3, o4));
    }

    /**
     * Given five Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @param o3
     *            an Flowable competing to react first
     * @param o4
     *            an Flowable competing to react first
     * @param o5
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2, Flowable<? extends T> o3, Flowable<? extends T> o4, Flowable<? extends T> o5) {
        return create(OnSubscribeAmb.amb(o1, o2, o3, o4, o5));
    }

    /**
     * Given six Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @param o3
     *            an Flowable competing to react first
     * @param o4
     *            an Flowable competing to react first
     * @param o5
     *            an Flowable competing to react first
     * @param o6
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2, Flowable<? extends T> o3, Flowable<? extends T> o4, Flowable<? extends T> o5, Flowable<? extends T> o6) {
        return create(OnSubscribeAmb.amb(o1, o2, o3, o4, o5, o6));
    }

    /**
     * Given seven Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @param o3
     *            an Flowable competing to react first
     * @param o4
     *            an Flowable competing to react first
     * @param o5
     *            an Flowable competing to react first
     * @param o6
     *            an Flowable competing to react first
     * @param o7
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2, Flowable<? extends T> o3, Flowable<? extends T> o4, Flowable<? extends T> o5, Flowable<? extends T> o6, Flowable<? extends T> o7) {
        return create(OnSubscribeAmb.amb(o1, o2, o3, o4, o5, o6, o7));
    }

    /**
     * Given eight Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @param o3
     *            an Flowable competing to react first
     * @param o4
     *            an Flowable competing to react first
     * @param o5
     *            an Flowable competing to react first
     * @param o6
     *            an Flowable competing to react first
     * @param o7
     *            an Flowable competing to react first
     * @param o8
     *            an observable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2, Flowable<? extends T> o3, Flowable<? extends T> o4, Flowable<? extends T> o5, Flowable<? extends T> o6, Flowable<? extends T> o7, Flowable<? extends T> o8) {
        return create(OnSubscribeAmb.amb(o1, o2, o3, o4, o5, o6, o7, o8));
    }

    /**
     * Given nine Flowables, mirrors the one that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            an Flowable competing to react first
     * @param o2
     *            an Flowable competing to react first
     * @param o3
     *            an Flowable competing to react first
     * @param o4
     *            an Flowable competing to react first
     * @param o5
     *            an Flowable competing to react first
     * @param o6
     *            an Flowable competing to react first
     * @param o7
     *            an Flowable competing to react first
     * @param o8
     *            an Flowable competing to react first
     * @param o9
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final static <T> Flowable<T> amb(Flowable<? extends T> o1, Flowable<? extends T> o2, Flowable<? extends T> o3, Flowable<? extends T> o4, Flowable<? extends T> o5, Flowable<? extends T> o6, Flowable<? extends T> o7, Flowable<? extends T> o8, Flowable<? extends T> o9) {
        return create(OnSubscribeAmb.amb(o1, o2, o3, o4, o5, o6, o7, o8, o9));
    }

    /**
     * Combines two source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from either of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Func2<? super T1, ? super T2, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2), Functions.fromFunc(combineFunction));
    }

    /**
     * Combines three source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param o3
     *            the third source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, T3, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Func3<? super T1, ? super T2, ? super T3, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2, o3), Functions.fromFunc(combineFunction));
    }

    /**
     * Combines four source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param o3
     *            the third source Flowable
     * @param o4
     *            the fourth source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, T3, T4, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4,
            Func4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2, o3, o4), Functions.fromFunc(combineFunction));
    }

    /**
     * Combines five source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param o3
     *            the third source Flowable
     * @param o4
     *            the fourth source Flowable
     * @param o5
     *            the fifth source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, T3, T4, T5, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5,
            Func5<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2, o3, o4, o5), Functions.fromFunc(combineFunction));
    }

    /**
     * Combines six source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param o3
     *            the third source Flowable
     * @param o4
     *            the fourth source Flowable
     * @param o5
     *            the fifth source Flowable
     * @param o6
     *            the sixth source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, T3, T4, T5, T6, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6,
            Func6<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2, o3, o4, o5, o6), Functions.fromFunc(combineFunction));
    }

    /**
     * Combines seven source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param o3
     *            the third source Flowable
     * @param o4
     *            the fourth source Flowable
     * @param o5
     *            the fifth source Flowable
     * @param o6
     *            the sixth source Flowable
     * @param o7
     *            the seventh source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, T3, T4, T5, T6, T7, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6, Flowable<? extends T7> o7,
            Func7<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2, o3, o4, o5, o6, o7), Functions.fromFunc(combineFunction));
    }

    /**
     * Combines eight source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param o3
     *            the third source Flowable
     * @param o4
     *            the fourth source Flowable
     * @param o5
     *            the fifth source Flowable
     * @param o6
     *            the sixth source Flowable
     * @param o7
     *            the seventh source Flowable
     * @param o8
     *            the eighth source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, T3, T4, T5, T6, T7, T8, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6, Flowable<? extends T7> o7, Flowable<? extends T8> o8,
            Func8<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2, o3, o4, o5, o6, o7, o8), Functions.fromFunc(combineFunction));
    }

    /**
     * Combines nine source Flowables by emitting an item that aggregates the latest values of each of the
     * source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/combineLatest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            the second source Flowable
     * @param o3
     *            the third source Flowable
     * @param o4
     *            the fourth source Flowable
     * @param o5
     *            the fifth source Flowable
     * @param o6
     *            the sixth source Flowable
     * @param o7
     *            the seventh source Flowable
     * @param o8
     *            the eighth source Flowable
     * @param o9
     *            the ninth source Flowable
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @SuppressWarnings("unchecked")
    public static final <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> Flowable<R> combineLatest(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6, Flowable<? extends T7> o7, Flowable<? extends T8> o8,
            Flowable<? extends T9> o9,
            Func9<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? super T9, ? extends R> combineFunction) {
        return combineLatest(Arrays.asList(o1, o2, o3, o4, o5, o6, o7, o8, o9), Functions.fromFunc(combineFunction));
    }
    /**
     * Combines a list of source Flowables by emitting an item that aggregates the latest values of each of
     * the source Flowables each time an item is received from any of the source Flowables, where this
     * aggregation is defined by a specified function.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code combineLatest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the common base type of source values
     * @param <R>
     *            the result type
     * @param sources
     *            the list of source Flowables
     * @param combineFunction
     *            the aggregation function used to combine the items emitted by the source Flowables
     * @return an Flowable that emits items that are the result of combining the items emitted by the source
     *         Flowables by means of the given aggregation function
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    public static final <T, R> Flowable<R> combineLatest(List<? extends Flowable<? extends T>> sources, FuncN<? extends R> combineFunction) {
        return create(new OnSubscribeCombineLatest<T, R>(sources, combineFunction));
    }

    /**
     * Returns an Flowable that emits the items emitted by each of the Flowables emitted by the source
     * Flowable, one after the other, without interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param observables
     *            an Flowable that emits Flowables
     * @return an Flowable that emits items all of the items emitted by the Flowables emitted by
     *         {@code observables}, one after the other, without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends Flowable<? extends T>> observables) {
        return observables.lift(OperatorConcat.<T>instance());
    }

    /**
     * Returns an Flowable that emits the items emitted by two Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the two source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2) {
        return concat(just(t1, t2));
    }

    /**
     * Returns an Flowable that emits the items emitted by three Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @param t3
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the three source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3) {
        return concat(just(t1, t2, t3));
    }

    /**
     * Returns an Flowable that emits the items emitted by four Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @param t3
     *            an Flowable to be concatenated
     * @param t4
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the four source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4) {
        return concat(just(t1, t2, t3, t4));
    }

    /**
     * Returns an Flowable that emits the items emitted by five Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @param t3
     *            an Flowable to be concatenated
     * @param t4
     *            an Flowable to be concatenated
     * @param t5
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the five source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5) {
        return concat(just(t1, t2, t3, t4, t5));
    }

    /**
     * Returns an Flowable that emits the items emitted by six Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @param t3
     *            an Flowable to be concatenated
     * @param t4
     *            an Flowable to be concatenated
     * @param t5
     *            an Flowable to be concatenated
     * @param t6
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the six source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6) {
        return concat(just(t1, t2, t3, t4, t5, t6));
    }

    /**
     * Returns an Flowable that emits the items emitted by seven Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @param t3
     *            an Flowable to be concatenated
     * @param t4
     *            an Flowable to be concatenated
     * @param t5
     *            an Flowable to be concatenated
     * @param t6
     *            an Flowable to be concatenated
     * @param t7
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the seven source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7) {
        return concat(just(t1, t2, t3, t4, t5, t6, t7));
    }

    /**
     * Returns an Flowable that emits the items emitted by eight Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @param t3
     *            an Flowable to be concatenated
     * @param t4
     *            an Flowable to be concatenated
     * @param t5
     *            an Flowable to be concatenated
     * @param t6
     *            an Flowable to be concatenated
     * @param t7
     *            an Flowable to be concatenated
     * @param t8
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the eight source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7, Flowable<? extends T> t8) {
        return concat(just(t1, t2, t3, t4, t5, t6, t7, t8));
    }

    /**
     * Returns an Flowable that emits the items emitted by nine Flowables, one after the other, without
     * interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be concatenated
     * @param t2
     *            an Flowable to be concatenated
     * @param t3
     *            an Flowable to be concatenated
     * @param t4
     *            an Flowable to be concatenated
     * @param t5
     *            an Flowable to be concatenated
     * @param t6
     *            an Flowable to be concatenated
     * @param t7
     *            an Flowable to be concatenated
     * @param t8
     *            an Flowable to be concatenated
     * @param t9
     *            an Flowable to be concatenated
     * @return an Flowable that emits items emitted by the nine source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final static <T> Flowable<T> concat(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7, Flowable<? extends T> t8, Flowable<? extends T> t9) {
        return concat(just(t1, t2, t3, t4, t5, t6, t7, t8, t9));
    }

    /**
     * Returns an Flowable that calls an Flowable factory to create an Flowable for each new Subscriber
     * that subscribes. That is, for each subscriber, the actual Flowable that subscriber observes is
     * determined by the factory function.
     * <p>
     * <img width="640" height="340" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/defer.png" alt="">
     * <p>
     * The defer Subscriber allows you to defer or delay emitting items from an Flowable until such time as an
     * Subscriber subscribes to the Flowable. This allows an {@link Subscriber} to easily obtain updates or a
     * refreshed version of the sequence.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code defer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param observableFactory
     *            the Flowable factory function to invoke for each {@link Subscriber} that subscribes to the
     *            resulting Flowable
     * @param <T>
     *            the type of the items emitted by the Flowable
     * @return an Flowable whose {@link Subscriber}s' subscriptions trigger an invocation of the given
     *         Flowable factory function
     * @see <a href="http://reactivex.io/documentation/operators/defer.html">ReactiveX operators documentation: Defer</a>
     */
    public final static <T> Flowable<T> defer(Supplier<Flowable<T>> observableFactory) {
        return create(new OnSubscribeDefer<T>(observableFactory));
    }

    /**
     * Converts a {@link Future} into an Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.Future.png" alt="">
     * <p>
     * You can convert any object that supports the {@link Future} interface into an Flowable that emits the
     * return value of the {@link Future#get} method of that object, by passing the object into the {@code from}
     * method.
     * <p>
     * <em>Important note:</em> This Flowable is blocking; you cannot unsubscribe from it.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param future
     *            the source {@link Future}
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to be emitted by
     *            the resulting Flowable
     * @return an Flowable that emits the item from the source {@link Future}
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    public final static <T> Flowable<T> from(Future<? extends T> future) {
        return create(OnSubscribeToFlowableFuture.toFlowableFuture(future));
    }

    /**
     * Converts a {@link Future} into an Flowable, with a timeout on the Future.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.Future.png" alt="">
     * <p>
     * You can convert any object that supports the {@link Future} interface into an Flowable that emits the
     * return value of the {@link Future#get} method of that object, by passing the object into the {@code from}
     * method.
     * <p>
     * <em>Important note:</em> This Flowable is blocking; you cannot unsubscribe from it.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code from} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param future
     *            the source {@link Future}
     * @param timeout
     *            the maximum time to wait before calling {@code get}
     * @param unit
     *            the {@link TimeUnit} of the {@code timeout} argument
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to be emitted by
     *            the resulting Flowable
     * @return an Flowable that emits the item from the source {@link Future}
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    public final static <T> Flowable<T> from(Future<? extends T> future, long timeout, TimeUnit unit) {
        return create(OnSubscribeToFlowableFuture.toFlowableFuture(future, timeout, unit));
    }

    /**
     * Converts a {@link Future}, operating on a specified {@link Scheduler}, into an Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/from.Future.s.png" alt="">
     * <p>
     * You can convert any object that supports the {@link Future} interface into an Flowable that emits the
     * return value of the {@link Future#get} method of that object, by passing the object into the {@code from}
     * method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param future
     *            the source {@link Future}
     * @param scheduler
     *            the {@link Scheduler} to wait for the Future on. Use a Scheduler such as
     *            {@link Schedulers#io()} that can block and wait on the Future
     * @param <T>
     *            the type of object that the {@link Future} returns, and also the type of item to be emitted by
     *            the resulting Flowable
     * @return an Flowable that emits the item from the source {@link Future}
     * @see <a href="http://reactivex.io/documentation/operators/from.html">ReactiveX operators documentation: From</a>
     */
    public final static <T> Flowable<T> from(Future<? extends T> future, Scheduler scheduler) {
        // TODO in a future revision the Scheduler will become important because we'll start polling instead of blocking on the Future
        return create(OnSubscribeToFlowableFuture.toFlowableFuture(future)).subscribeOn(scheduler);
    }

    /**
     * Converts two items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2) {
        return from(Arrays.asList(t1, t2));
    }

    /**
     * Converts three items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3) {
        return from(Arrays.asList(t1, t2, t3));
    }

    /**
     * Converts four items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param t4
     *            fourth item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3, T t4) {
        return from(Arrays.asList(t1, t2, t3, t4));
    }

    /**
     * Converts five items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param t4
     *            fourth item
     * @param t5
     *            fifth item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3, T t4, T t5) {
        return from(Arrays.asList(t1, t2, t3, t4, t5));
    }

    /**
     * Converts six items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param t4
     *            fourth item
     * @param t5
     *            fifth item
     * @param t6
     *            sixth item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3, T t4, T t5, T t6) {
        return from(Arrays.asList(t1, t2, t3, t4, t5, t6));
    }

    /**
     * Converts seven items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param t4
     *            fourth item
     * @param t5
     *            fifth item
     * @param t6
     *            sixth item
     * @param t7
     *            seventh item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3, T t4, T t5, T t6, T t7) {
        return from(Arrays.asList(t1, t2, t3, t4, t5, t6, t7));
    }

    /**
     * Converts eight items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param t4
     *            fourth item
     * @param t5
     *            fifth item
     * @param t6
     *            sixth item
     * @param t7
     *            seventh item
     * @param t8
     *            eighth item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3, T t4, T t5, T t6, T t7, T t8) {
        return from(Arrays.asList(t1, t2, t3, t4, t5, t6, t7, t8));
    }

    /**
     * Converts nine items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param t4
     *            fourth item
     * @param t5
     *            fifth item
     * @param t6
     *            sixth item
     * @param t7
     *            seventh item
     * @param t8
     *            eighth item
     * @param t9
     *            ninth item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3, T t4, T t5, T t6, T t7, T t8, T t9) {
        return from(Arrays.asList(t1, t2, t3, t4, t5, t6, t7, t8, t9));
    }

    /**
     * Converts ten items into an Flowable that emits those items.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/just.m.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code just} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            first item
     * @param t2
     *            second item
     * @param t3
     *            third item
     * @param t4
     *            fourth item
     * @param t5
     *            fifth item
     * @param t6
     *            sixth item
     * @param t7
     *            seventh item
     * @param t8
     *            eighth item
     * @param t9
     *            ninth item
     * @param t10
     *            tenth item
     * @param <T>
     *            the type of these items
     * @return an Flowable that emits each item
     * @see <a href="http://reactivex.io/documentation/operators/just.html">ReactiveX operators documentation: Just</a>
     */
    // suppress unchecked because we are using varargs inside the method
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> just(T t1, T t2, T t3, T t4, T t5, T t6, T t7, T t8, T t9, T t10) {
        return from(Arrays.asList(t1, t2, t3, t4, t5, t6, t7, t8, t9, t10));
    }

    /**
     * Flattens two Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2) {
        return merge(from(Arrays.asList(t1, t2)));
    }

    /**
     * Flattens three Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3) {
        return merge(from(Arrays.asList(t1, t2, t3)));
    }

    /**
     * Flattens four Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4) {
        return merge(from(Arrays.asList(t1, t2, t3, t4)));
    }

    /**
     * Flattens five Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5) {
        return merge(from(Arrays.asList(t1, t2, t3, t4, t5)));
    }

    /**
     * Flattens six Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6) {
        return merge(from(Arrays.asList(t1, t2, t3, t4, t5, t6)));
    }

    /**
     * Flattens seven Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @param t7
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7) {
        return merge(from(Arrays.asList(t1, t2, t3, t4, t5, t6, t7)));
    }

    /**
     * Flattens eight Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @param t7
     *            an Flowable to be merged
     * @param t8
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7, Flowable<? extends T> t8) {
        return merge(from(Arrays.asList(t1, t2, t3, t4, t5, t6, t7, t8)));
    }

    /**
     * Flattens nine Flowables into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code merge} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code merge} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @param t7
     *            an Flowable to be merged
     * @param t8
     *            an Flowable to be merged
     * @param t9
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    @SuppressWarnings("unchecked")
    public final static <T> Flowable<T> merge(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7, Flowable<? extends T> t8, Flowable<? extends T> t9) {
        return merge(from(Arrays.asList(t1, t2, t3, t4, t5, t6, t7, t8, t9)));
    }

    /**
     * Flattens two Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from each of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable, Flowable)} except that if any of the merged Flowables
     * notify of an error via {@link Subscriber#onError onError}, {@code mergeDelayError} will refrain from
     * propagating that error notification until all of the merged Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if both merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the two source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2) {
        return mergeDelayError(just(t1, t2));
    }

    /**
     * Flattens three Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from all of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable, Flowable, Flowable)} except that if any of the merged
     * Flowables notify of an error via {@link Subscriber#onError onError}, {@code mergeDelayError} will refrain
     * from propagating that error notification until all of the merged Flowables have finished emitting
     * items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3) {
        return mergeDelayError(just(t1, t2, t3));
    }

    /**
     * Flattens four Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from all of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable, Flowable, Flowable, Flowable)} except that if any of
     * the merged Flowables notify of an error via {@link Subscriber#onError onError}, {@code mergeDelayError}
     * will refrain from propagating that error notification until all of the merged Flowables have finished
     * emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4) {
        return mergeDelayError(just(t1, t2, t3, t4));
    }

    /**
     * Flattens five Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from all of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable, Flowable, Flowable, Flowable, Flowable)} except that
     * if any of the merged Flowables notify of an error via {@link Subscriber#onError onError},
     * {@code mergeDelayError} will refrain from propagating that error notification until all of the merged
     * Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5) {
        return mergeDelayError(just(t1, t2, t3, t4, t5));
    }

    /**
     * Flattens six Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from all of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable, Flowable, Flowable, Flowable, Flowable, Flowable)}
     * except that if any of the merged Flowables notify of an error via {@link Subscriber#onError onError},
     * {@code mergeDelayError} will refrain from propagating that error notification until all of the merged
     * Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6) {
        return mergeDelayError(just(t1, t2, t3, t4, t5, t6));
    }

    /**
     * Flattens seven Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from all of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like
     * {@link #merge(Flowable, Flowable, Flowable, Flowable, Flowable, Flowable, Flowable)}
     * except that if any of the merged Flowables notify of an error via {@link Subscriber#onError onError},
     * {@code mergeDelayError} will refrain from propagating that error notification until all of the merged
     * Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @param t7
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7) {
        return mergeDelayError(just(t1, t2, t3, t4, t5, t6, t7));
    }

    /**
     * Flattens eight Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from all of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable, Flowable, Flowable, Flowable, Flowable, Flowable, Flowable, Flowable)}
     * except that if any of the merged Flowables notify of an error via {@link Subscriber#onError onError},
     * {@code mergeDelayError} will refrain from propagating that error notification until all of the merged
     * Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @param t7
     *            an Flowable to be merged
     * @param t8
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    // suppress because the types are checked by the method signature before using a vararg
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7, Flowable<? extends T> t8) {
        return mergeDelayError(just(t1, t2, t3, t4, t5, t6, t7, t8));
    }

    /**
     * Flattens nine Flowables into one Flowable, in a way that allows an Subscriber to receive all
     * successfully emitted items from all of the source Flowables without being interrupted by an error
     * notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable, Flowable, Flowable, Flowable, Flowable, Flowable, Flowable, Flowable, Flowable)}
     * except that if any of the merged Flowables notify of an error via {@link Subscriber#onError onError},
     * {@code mergeDelayError} will refrain from propagating that error notification until all of the merged
     * Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Subscribers once.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeDelayError} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @param t2
     *            an Flowable to be merged
     * @param t3
     *            an Flowable to be merged
     * @param t4
     *            an Flowable to be merged
     * @param t5
     *            an Flowable to be merged
     * @param t6
     *            an Flowable to be merged
     * @param t7
     *            an Flowable to be merged
     * @param t8
     *            an Flowable to be merged
     * @param t9
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items that are emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final static <T> Flowable<T> mergeDelayError(Flowable<? extends T> t1, Flowable<? extends T> t2, Flowable<? extends T> t3, Flowable<? extends T> t4, Flowable<? extends T> t5, Flowable<? extends T> t6, Flowable<? extends T> t7, Flowable<? extends T> t8, Flowable<? extends T> t9) {
        return mergeDelayError(just(t1, t2, t3, t4, t5, t6, t7, t8, t9));
    }

    /**
     * Converts the source {@code Flowable<T>} into an {@code Flowable<Flowable<T>>} that emits the
     * source Flowable as its single emission.
     * <p>
     * <img width="640" height="350" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/nest.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code nest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits a single item: the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final Flowable<Flowable<T>> nest() {
        return just(this);
    }

    /**
     * Returns an Flowable that emits a sequence of Integers within a specified range, on a specified
     * Scheduler.
     * <p>
     * <img width="640" height="195" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/range.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param start
     *            the value of the first Integer in the sequence
     * @param count
     *            the number of sequential Integers to generate
     * @param scheduler
     *            the Scheduler to run the generator loop on
     * @return an Flowable that emits a range of sequential Integers
     * @see <a href="http://reactivex.io/documentation/operators/range.html">ReactiveX operators documentation: Range</a>
     */
    public final static Flowable<Integer> range(int start, int count, Scheduler scheduler) {
        return range(start, count).subscribeOn(scheduler);
    }

    /**
     * Returns an Flowable that emits a Boolean value that indicates whether two Flowable sequences are the
     * same by comparing the items emitted by each Flowable pairwise.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sequenceEqual.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sequenceEqual} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param first
     *            the first Flowable to compare
     * @param second
     *            the second Flowable to compare
     * @param <T>
     *            the type of items emitted by each Flowable
     * @return an Flowable that emits a Boolean value that indicates whether the two sequences are the same
     * @see <a href="http://reactivex.io/documentation/operators/sequenceequal.html">ReactiveX operators documentation: SequenceEqual</a>
     */
    public final static <T> Flowable<Boolean> sequenceEqual(Flowable<? extends T> first, Flowable<? extends T> second) {
        return sequenceEqual(first, second, (a, b) -> {
                if (a == null) {
                    return b == null;
                }
                return a.equals(b);
            }
        );
    }

    /**
     * Returns an Flowable that emits a Boolean value that indicates whether two Flowable sequences are the
     * same by comparing the items emitted by each Flowable pairwise based on the results of a specified
     * equality function.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sequenceEqual.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sequenceEqual} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param first
     *            the first Flowable to compare
     * @param second
     *            the second Flowable to compare
     * @param equality
     *            a function used to compare items emitted by each Flowable
     * @param <T>
     *            the type of items emitted by each Flowable
     * @return an Flowable that emits a Boolean value that indicates whether the two Flowable two sequences
     *         are the same according to the specified function
     * @see <a href="http://reactivex.io/documentation/operators/sequenceequal.html">ReactiveX operators documentation: SequenceEqual</a>
     */
    public final static <T> Flowable<Boolean> sequenceEqual(Flowable<? extends T> first, Flowable<? extends T> second, BiPredicate<? super T, ? super T> equality) {
        return OperatorSequenceEqual.sequenceEqual(first, second, equality);
    }

    /**
     * Converts an Flowable that emits Flowables into an Flowable that emits the items emitted by the
     * most recently emitted of those Flowables.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchDo.png" alt="">
     * <p>
     * {@code switchOnNext} subscribes to an Flowable that emits Flowables. Each time it observes one of
     * these emitted Flowables, the Flowable returned by {@code switchOnNext} begins emitting the items
     * emitted by that Flowable. When a new Flowable is emitted, {@code switchOnNext} stops emitting items
     * from the earlier-emitted Flowable and begins emitting items from the new one.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchOnNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T> the item type
     * @param sequenceOfSequences
     *            the source Flowable that emits Flowables
     * @return an Flowable that emits the items emitted by the Flowable most recently emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/switch.html">ReactiveX operators documentation: Switch</a>
     */
    public final static <T> Flowable<T> switchOnNext(Flowable<? extends Flowable<? extends T>> sequenceOfSequences) {
        return sequenceOfSequences.lift(OperatorSwitch.<T>instance());
    }

    /**
     * Constructs an Flowable that creates a dependent resource object which is disposed of on unsubscription.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/using.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code using} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param resourceFactory
     *            the factory function to create a resource object that depends on the Flowable
     * @param observableFactory
     *            the factory function to create an Flowable
     * @param disposeAction
     *            the function that will dispose of the resource
     * @return the Flowable whose lifetime controls the lifetime of the dependent resource object
     * @see <a href="http://reactivex.io/documentation/operators/using.html">ReactiveX operators documentation: Using</a>
     */
    public final static <T, Resource> Flowable<T> using(
            final Supplier<Resource> resourceFactory,
            final Function<? super Resource, ? extends Flowable<? extends T>> observableFactory,
            final Consumer<? super Resource> disposeAction) {
        return using(resourceFactory, observableFactory, disposeAction, false);
    }
    
    /**
     * Constructs an Flowable that creates a dependent resource object which is disposed of just before 
     * termination if you have set {@code disposeEagerly} to {@code true} and unsubscription does not occur
     * before termination. Otherwise resource disposal will occur on unsubscription.  Eager disposal is
     * particularly appropriate for a synchronous Flowable that resuses resources. {@code disposeAction} will
     * only be called once per subscription.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/using.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code using} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @warn "Backpressure Support" section missing from javadoc
     * @param resourceFactory
     *            the factory function to create a resource object that depends on the Flowable
     * @param observableFactory
     *            the factory function to create an Flowable
     * @param disposeAction
     *            the function that will dispose of the resource
     * @param disposeEagerly
     *            if {@code true} then disposal will happen either on unsubscription or just before emission of 
     *            a terminal event ({@code onComplete} or {@code onError}).
     * @return the Flowable whose lifetime controls the lifetime of the dependent resource object
     * @see <a href="http://reactivex.io/documentation/operators/using.html">ReactiveX operators documentation: Using</a>
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace
     *        this parenthetical with the release number)
     */
    @Experimental
    public final static <T, Resource> Flowable<T> using(
            final Supplier<Resource> resourceFactory,
            final Function<? super Resource, ? extends Flowable<? extends T>> observableFactory,
            final Consumer<? super Resource> disposeAction, boolean disposeEagerly) {
        return create(new OnSubscribeUsing<T, Resource>(resourceFactory, observableFactory, disposeAction, disposeEagerly));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * items emitted, in sequence, by an Iterable of other Flowables.
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by each of the source Flowables;
     * the second item emitted by the new Flowable will be the result of the function applied to the second
     * item emitted by each of those Flowables; and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@code onNext} as many times as
     * the number of {@code onNext} invokations of the source Flowable that emits the fewest items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param ws
     *            an Iterable of source Flowables
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <R> Flowable<R> zip(Iterable<? extends Flowable<?>> ws, FuncN<? extends R> zipFunction) {
        List<Flowable<?>> os = new ArrayList<Flowable<?>>();
        for (Flowable<?> o : ws) {
            os.add(o);
        }
        return Flowable.just(os.toArray(new Flowable<?>[os.size()])).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * <i>n</i> items emitted, in sequence, by the <i>n</i> Flowables emitted by a specified Flowable.
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by each of the Flowables emitted
     * by the source Flowable; the second item emitted by the new Flowable will be the result of the
     * function applied to the second item emitted by each of those Flowables; and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@code onNext} as many times as
     * the number of {@code onNext} invokations of the source Flowable that emits the fewest items.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param ws
     *            an Flowable of source Flowables
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the Flowables emitted by
     *            {@code ws}, results in an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <R> Flowable<R> zip(Flowable<? extends Flowable<?>> ws, final FuncN<? extends R> zipFunction) {
        return ws.toList().map(new Function<List<? extends Flowable<?>>, Flowable<?>[]>() {

            @Override
            public Flowable<?>[] call(List<? extends Flowable<?>> o) {
                return o.toArray(new Flowable<?>[o.size()]);
            }

        }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * two items emitted, in sequence, by two other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by {@code o1} and the first item
     * emitted by {@code o2}; the second item emitted by the new Flowable will be the result of the function
     * applied to the second item emitted by {@code o1} and the second item emitted by {@code o2}; and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results
     *            in an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, final Func2<? super T1, ? super T2, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * three items emitted, in sequence, by three other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by {@code o1}, the first item
     * emitted by {@code o2}, and the first item emitted by {@code o3}; the second item emitted by the new
     * Flowable will be the result of the function applied to the second item emitted by {@code o1}, the
     * second item emitted by {@code o2}, and the second item emitted by {@code o3}; and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param o3
     *            a third source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, T3, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Func3<? super T1, ? super T2, ? super T3, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2, o3 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * four items emitted, in sequence, by four other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by {@code o1}, the first item
     * emitted by {@code o2}, the first item emitted by {@code o3}, and the first item emitted by {@code 04};
     * the second item emitted by the new Flowable will be the result of the function applied to the second
     * item emitted by each of those Flowables; and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param o3
     *            a third source Flowable
     * @param o4
     *            a fourth source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, T3, T4, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Func4<? super T1, ? super T2, ? super T3, ? super T4, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2, o3, o4 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * five items emitted, in sequence, by five other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by {@code o1}, the first item
     * emitted by {@code o2}, the first item emitted by {@code o3}, the first item emitted by {@code o4}, and
     * the first item emitted by {@code o5}; the second item emitted by the new Flowable will be the result of
     * the function applied to the second item emitted by each of those Flowables; and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param o3
     *            a third source Flowable
     * @param o4
     *            a fourth source Flowable
     * @param o5
     *            a fifth source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, T3, T4, T5, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Func5<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2, o3, o4, o5 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * six items emitted, in sequence, by six other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by each source Flowable, the
     * second item emitted by the new Flowable will be the result of the function applied to the second item
     * emitted by each of those Flowables, and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param o3
     *            a third source Flowable
     * @param o4
     *            a fourth source Flowable
     * @param o5
     *            a fifth source Flowable
     * @param o6
     *            a sixth source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, T3, T4, T5, T6, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6,
            Func6<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2, o3, o4, o5, o6 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * seven items emitted, in sequence, by seven other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by each source Flowable, the
     * second item emitted by the new Flowable will be the result of the function applied to the second item
     * emitted by each of those Flowables, and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param o3
     *            a third source Flowable
     * @param o4
     *            a fourth source Flowable
     * @param o5
     *            a fifth source Flowable
     * @param o6
     *            a sixth source Flowable
     * @param o7
     *            a seventh source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, T3, T4, T5, T6, T7, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6, Flowable<? extends T7> o7,
            Func7<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2, o3, o4, o5, o6, o7 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * eight items emitted, in sequence, by eight other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by each source Flowable, the
     * second item emitted by the new Flowable will be the result of the function applied to the second item
     * emitted by each of those Flowables, and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param o3
     *            a third source Flowable
     * @param o4
     *            a fourth source Flowable
     * @param o5
     *            a fifth source Flowable
     * @param o6
     *            a sixth source Flowable
     * @param o7
     *            a seventh source Flowable
     * @param o8
     *            an eighth source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, T3, T4, T5, T6, T7, T8, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6, Flowable<? extends T7> o7, Flowable<? extends T8> o8,
            Func8<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2, o3, o4, o5, o6, o7, o8 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits the results of a specified combiner function applied to combinations of
     * nine items emitted, in sequence, by nine other Flowables.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <p>
     * {@code zip} applies this function in strict sequence, so the first item emitted by the new Flowable
     * will be the result of the function applied to the first item emitted by each source Flowable, the
     * second item emitted by the new Flowable will be the result of the function applied to the second item
     * emitted by each of those Flowables, and so forth.
     * <p>
     * The resulting {@code Flowable<R>} returned from {@code zip} will invoke {@link Subscriber#onNext onNext}
     * as many times as the number of {@code onNext} invocations of the source Flowable that emits the fewest
     * items.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param o1
     *            the first source Flowable
     * @param o2
     *            a second source Flowable
     * @param o3
     *            a third source Flowable
     * @param o4
     *            a fourth source Flowable
     * @param o5
     *            a fifth source Flowable
     * @param o6
     *            a sixth source Flowable
     * @param o7
     *            a seventh source Flowable
     * @param o8
     *            an eighth source Flowable
     * @param o9
     *            a ninth source Flowable
     * @param zipFunction
     *            a function that, when applied to an item emitted by each of the source Flowables, results in
     *            an item that will be emitted by the resulting Flowable
     * @return an Flowable that emits the zipped results
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final static <T1, T2, T3, T4, T5, T6, T7, T8, T9, R> Flowable<R> zip(Flowable<? extends T1> o1, Flowable<? extends T2> o2, Flowable<? extends T3> o3, Flowable<? extends T4> o4, Flowable<? extends T5> o5, Flowable<? extends T6> o6, Flowable<? extends T7> o7, Flowable<? extends T8> o8,
            Flowable<? extends T9> o9, Func9<? super T1, ? super T2, ? super T3, ? super T4, ? super T5, ? super T6, ? super T7, ? super T8, ? super T9, ? extends R> zipFunction) {
        return just(new Flowable<?>[] { o1, o2, o3, o4, o5, o6, o7, o8, o9 }).lift(new OperatorZip<R>(zipFunction));
    }

    /**
     * Returns an Flowable that emits a Boolean that indicates whether all of the items emitted by the source
     * Flowable satisfy a condition.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/all.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code all} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function that evaluates an item and returns a Boolean
     * @return an Flowable that emits {@code true} if all items emitted by the source Flowable satisfy the
     *         predicate; otherwise, {@code false}
     * @see <a href="http://reactivex.io/documentation/operators/all.html">ReactiveX operators documentation: All</a>
     */
    public final Flowable<Boolean> all(Prdicate<? super T> predicate) {
        return lift(new OperatorAll<T>(predicate));
    }
    
    /**
     * Mirrors the Flowable (current or provided) that first either emits an item or sends a termination
     * notification.
     * <p>
     * <img width="640" height="385" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/amb.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code amb} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable competing to react first
     * @return an Flowable that emits the same sequence as whichever of the source Flowables first
     *         emitted an item or sent a termination notification
     * @see <a href="http://reactivex.io/documentation/operators/amb.html">ReactiveX operators documentation: Amb</a>
     */
    public final Flowable<T> ambWith(Flowable<? extends T> t1) {
        return amb(this, t1);
    }

    /**
     * Portrays a object of an Flowable subclass as a simple Flowable object. This is useful, for instance,
     * when you have an implementation of a subclass of Flowable but you want to hide the properties and
     * methods of this subclass from whomever you are passing the Flowable to.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code asFlowable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that hides the identity of this Flowable
     */
    public final Flowable<T> asFlowable() {
        return lift(OperatorAsFlowable.<T>instance());
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping buffers. It emits the current buffer and replaces it with a
     * new buffer whenever the Flowable produced by the specified {@code bufferClosingSelector} emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer1.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it is instead controlled by the given Flowables and
     *      buffers data. It requests {@code Long.MAX_VALUE} upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param bufferClosingSelector
     *            a {@link Supplier} that produces an Flowable that governs the boundary between buffers.
     *            Whenever this {@code Flowable} emits an item, {@code buffer} emits the current buffer and
     *            begins to fill a new one
     * @return an Flowable that emits a connected, non-overlapping buffer of items from the source Flowable
     *         each time the Flowable created with the {@code bufferClosingSelector} argument emits an item
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final <TClosing> Flowable<List<T>> buffer(Supplier<? extends Flowable<? extends TClosing>> bufferClosingSelector) {
        return lift(new OperatorBufferWithSingleFlowable<T, TClosing>(bufferClosingSelector, 16));
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping buffers, each containing {@code count} items. When the source
     * Flowable completes or encounters an error, the resulting Flowable emits the current buffer and
     * propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer3.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items in each buffer before it should be emitted
     * @return an Flowable that emits connected, non-overlapping buffers, each containing at most
     *         {@code count} items from the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(int count) {
        return buffer(count, count);
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits buffers every {@code skip} items, each containing {@code count} items. When the source
     * Flowable completes or encounters an error, the resulting Flowable emits the current buffer and
     * propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer4.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum size of each buffer before it should be emitted
     * @param skip
     *            how many items emitted by the source Flowable should be skipped before starting a new
     *            buffer. Note that when {@code skip} and {@code count} are equal, this is the same operation as
     *            {@link #buffer(int)}.
     * @return an Flowable that emits buffers for every {@code skip} item from the source Flowable and
     *         containing at most {@code count} items
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(int count, int skip) {
        return lift(new OperatorBufferWithSize<T>(count, skip));
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable starts a new buffer periodically, as determined by the {@code timeshift} argument. It emits
     * each buffer after a fixed timespan, specified by the {@code timespan} argument. When the source
     * Flowable completes or encounters an error, the resulting Flowable emits the current buffer and
     * propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer7.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. It requests {@code Long.MAX_VALUE}
     *      upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted
     * @param timeshift
     *            the period of time after which a new buffer will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @return an Flowable that emits new buffers of items emitted by the source Flowable periodically after
     *         a fixed timespan has elapsed
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(long timespan, long timeshift, TimeUnit unit) {
        return buffer(timespan, timeshift, unit,  Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable starts a new buffer periodically, as determined by the {@code timeshift} argument, and on the
     * specified {@code scheduler}. It emits each buffer after a fixed timespan, specified by the
     * {@code timespan} argument. When the source Flowable completes or encounters an error, the resulting
     * Flowable emits the current buffer and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer7.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. It requests {@code Long.MAX_VALUE}
     *      upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted
     * @param timeshift
     *            the period of time after which a new buffer will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @return an Flowable that emits new buffers of items emitted by the source Flowable periodically after
     *         a fixed timespan has elapsed
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(long timespan, long timeshift, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorBufferWithTime<T>(timespan, timeshift, unit, Integer.MAX_VALUE, scheduler));
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument. When the source Flowable completes or encounters an error, the resulting
     * Flowable emits the current buffer and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer5.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. It requests {@code Long.MAX_VALUE}
     *      upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time that applies to the {@code timespan} argument
     * @return an Flowable that emits connected, non-overlapping buffers of items emitted by the source
     *         Flowable within a fixed duration
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(long timespan, TimeUnit unit) {
        return buffer(timespan, unit, Integer.MAX_VALUE, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument or a maximum size specified by the {@code count} argument (whichever is reached
     * first). When the source Flowable completes or encounters an error, the resulting Flowable emits the
     * current buffer and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer6.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. It requests {@code Long.MAX_VALUE}
     *      upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each buffer before it is emitted
     * @return an Flowable that emits connected, non-overlapping buffers of items emitted by the source
     *         Flowable, after a fixed duration or when the buffer reaches maximum capacity (whichever occurs
     *         first)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(long timespan, TimeUnit unit, int count) {
        return lift(new OperatorBufferWithTime<T>(timespan, timespan, unit, count, Schedulers.computation()));
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument as measured on the specified {@code scheduler}, or a maximum size specified by
     * the {@code count} argument (whichever is reached first). When the source Flowable completes or
     * encounters an error, the resulting Flowable emits the current buffer and propagates the notification
     * from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer6.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. It requests {@code Long.MAX_VALUE}
     *      upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each buffer before it is emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @return an Flowable that emits connected, non-overlapping buffers of items emitted by the source
     *         Flowable after a fixed duration or when the buffer reaches maximum capacity (whichever occurs
     *         first)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(long timespan, TimeUnit unit, int count, Scheduler scheduler) {
        return lift(new OperatorBufferWithTime<T>(timespan, timespan, unit, count, scheduler));
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping buffers, each of a fixed duration specified by the
     * {@code timespan} argument and on the specified {@code scheduler}. When the source Flowable completes or
     * encounters an error, the resulting Flowable emits the current buffer and propagates the notification
     * from the source Flowable.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer5.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time. It requests {@code Long.MAX_VALUE}
     *      upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each buffer collects items before it is emitted and replaced with a new
     *            buffer
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a buffer
     * @return an Flowable that emits connected, non-overlapping buffers of items emitted by the source
     *         Flowable within a fixed duration
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final Flowable<List<T>> buffer(long timespan, TimeUnit unit, Scheduler scheduler) {
        return buffer(timespan, timespan, unit, scheduler);
    }

    /**
     * Returns an Flowable that emits buffers of items it collects from the source Flowable. The resulting
     * Flowable emits buffers that it creates when the specified {@code bufferOpenings} Flowable emits an
     * item, and closes when the Flowable returned from {@code bufferClosingSelector} emits an item.
     * <p>
     * <img width="640" height="470" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer2.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it is instead controlled by the given Flowables and
     *      buffers data. It requests {@code Long.MAX_VALUE} upstream and does not obey downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param bufferOpenings
     *            the Flowable that, when it emits an item, causes a new buffer to be created
     * @param bufferClosingSelector
     *            the {@link Function} that is used to produce an Flowable for every buffer created. When this
     *            Flowable emits an item, the associated buffer is emitted.
     * @return an Flowable that emits buffers, containing items from the source Flowable, that are created
     *         and closed when the specified Flowables emit items
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final <TOpening, TClosing> Flowable<List<T>> buffer(Flowable<? extends TOpening> bufferOpenings, Function<? super TOpening, ? extends Flowable<? extends TClosing>> bufferClosingSelector) {
        return lift(new OperatorBufferWithStartEndFlowable<T, TOpening, TClosing>(bufferOpenings, bufferClosingSelector));
    }

    /**
     * Returns an Flowable that emits non-overlapping buffered items from the source Flowable each time the
     * specified boundary Flowable emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer8.png" alt="">
     * <p>
     * Completion of either the source or the boundary Flowable causes the returned Flowable to emit the
     * latest buffer and complete.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it is instead controlled by the {@code Flowable}
     *      {@code boundary} and buffers data. It requests {@code Long.MAX_VALUE} upstream and does not obey
     *      downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B>
     *            the boundary value type (ignored)
     * @param boundary
     *            the boundary Flowable
     * @return an Flowable that emits buffered items from the source Flowable when the boundary Flowable
     *         emits an item
     * @see #buffer(rx.Flowable, int)
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     */
    public final <B> Flowable<List<T>> buffer(Flowable<B> boundary) {
        return buffer(boundary, 16);
    }

    /**
     * Returns an Flowable that emits non-overlapping buffered items from the source Flowable each time the
     * specified boundary Flowable emits an item.
     * <p>
     * <img width="640" height="395" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/buffer8.png" alt="">
     * <p>
     * Completion of either the source or the boundary Flowable causes the returned Flowable to emit the
     * latest buffer and complete.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it is instead controlled by the {@code Flowable}
     *      {@code boundary} and buffers data. It requests {@code Long.MAX_VALUE} upstream and does not obey
     *      downstream requests.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code buffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <B>
     *            the boundary value type (ignored)
     * @param boundary
     *            the boundary Flowable
     * @param initialCapacity
     *            the initial capacity of each buffer chunk
     * @return an Flowable that emits buffered items from the source Flowable when the boundary Flowable
     *         emits an item
     * @see <a href="http://reactivex.io/documentation/operators/buffer.html">ReactiveX operators documentation: Buffer</a>
     * @see #buffer(rx.Flowable, int)
     */
    public final <B> Flowable<List<T>> buffer(Flowable<B> boundary, int initialCapacity) {
        return lift(new OperatorBufferWithSingleFlowable<T, B>(boundary, initialCapacity));
    }

    /**
     * Caches the emissions from the source Flowable and replays them in order to any subsequent Subscribers.
     * This method has similar behavior to {@link #replay} except that this auto-subscribes to the source
     * Flowable rather than returning a {@link ConnectableFlowable} for which you must call
     * {@code connect} to activate the subscription.
     * <p>
     * <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/cache.png" alt="">
     * <p>
     * This is useful when you want an Flowable to cache responses and you can't control the
     * subscribe/unsubscribe behavior of all the {@link Subscriber}s.
     * <p>
     * When you call {@code cache}, it does not yet subscribe to the source Flowable and so does not yet
     * begin cacheing items. This only happens when the first Subscriber calls the resulting Flowable's
     * {@code subscribe} method.
     * <p>
     * <em>Note:</em> You sacrifice the ability to unsubscribe from the origin when you use the {@code cache}
     * Subscriber so be careful not to use this Subscriber on Flowables that emit an infinite or very large number
     * of items that will use up memory.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support upstream backpressure as it is purposefully requesting and caching
     *      everything emitted.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code cache} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that, when first subscribed to, caches all of its items and notifications for the
     *         benefit of subsequent subscribers
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final Flowable<T> cache() {
        return create(new OnSubscribeCache<T>(this));
    }

    /**
     * Caches emissions from the source Flowable and replays them in order to any subsequent Subscribers.
     * This method has similar behavior to {@link #replay} except that this auto-subscribes to the source
     * Flowable rather than returning a {@link ConnectableFlowable} for which you must call
     * {@code connect} to activate the subscription.
     * <p>
     * <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/cache.png" alt="">
     * <p>
     * This is useful when you want an Flowable to cache responses and you can't control the
     * subscribe/unsubscribe behavior of all the {@link Subscriber}s.
     * <p>
     * When you call {@code cache}, it does not yet subscribe to the source Flowable and so does not yet
     * begin cacheing items. This only happens when the first Subscriber calls the resulting Flowable's
     * {@code subscribe} method.
     * <p>
     * <em>Note:</em> You sacrifice the ability to unsubscribe from the origin when you use the {@code cache}
     * Subscriber so be careful not to use this Subscriber on Flowables that emit an infinite or very large number
     * of items that will use up memory.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support upstream backpressure as it is purposefully requesting and caching
     *      everything emitted.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code cache} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param capacity hint for number of items to cache (for optimizing underlying data structure)
     * @return an Flowable that, when first subscribed to, caches all of its items and notifications for the
     *         benefit of subsequent subscribers
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final Flowable<T> cache(int capacity) {
        return create(new OnSubscribeCache<T>(this, capacity));
    }

    /**
     * Returns an Flowable that emits the items emitted by the source Flowable, converted to the specified
     * type.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/cast.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code cast} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param klass
     *            the target class type that {@code cast} will cast the items emitted by the source Flowable
     *            into before emitting them from the resulting Flowable
     * @return an Flowable that emits each item from the source Flowable after converting it to the
     *         specified type
     * @see <a href="http://reactivex.io/documentation/operators/map.html">ReactiveX operators documentation: Map</a>
     */
    public final <R> Flowable<R> cast(final Class<R> klass) {
        return lift(new OperatorCast<T, R>(klass));
    }

    /**
     * Collects items emitted by the source Flowable into a single mutable data structure and returns an
     * Flowable that emits this structure.
     * <p>
     * <img width="640" height="330" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/collect.png" alt="">
     * <p>
     * This is a simplified version of {@code reduce} that does not need to return the state on each pass.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because by intent it will receive all values and reduce
     *      them to a single {@code onNext}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code collect} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param stateFactory
     *           the mutable data structure that will collect the items
     * @param collector
     *           a function that accepts the {@code state} and an emitted item, and modifies {@code state}
     *           accordingly
     * @return an Flowable that emits the result of collecting the values emitted by the source Flowable
     *         into a single mutable data structure
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     */
    public final <R> Flowable<R> collect(Supplier<R> stateFactory, final Action2<R, ? super T> collector) {
        Func2<R, T, R> accumulator = new Func2<R, T, R>() {

            @Override
            public final R call(R state, T value) {
                collector.call(state, value);
                return state;
            }

        };
        
        /*
         * Discussion and confirmation of implementation at
         * https://github.com/ReactiveX/RxJava/issues/423#issuecomment-27642532
         * 
         * It should use last() not takeLast(1) since it needs to emit an error if the sequence is empty.
         */
        return lift(new OperatorScan<R, T>(stateFactory, accumulator)).last();
    }

    /**
     * Returns a new Flowable that emits items resulting from applying a function that you supply to each item
     * emitted by the source Flowable, where that function returns an Flowable, and then emitting the items
     * that result from concatinating those resulting Flowables.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concatMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param func
     *            a function that, when applied to an item emitted by the source Flowable, returns an
     *            Flowable
     * @return an Flowable that emits the result of applying the transformation function to each item emitted
     *         by the source Flowable and concatinating the Flowables obtained from this transformation
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <R> Flowable<R> concatMap(Function<? super T, ? extends Flowable<? extends R>> func) {
        return concat(map(func));
    }
    
    /**
     * Returns an Flowable that emits the items emitted from the current Flowable, then the next, one after
     * the other, without interleaving them.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/concat.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code concat} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be concatenated after the current
     * @return an Flowable that emits items emitted by the two source Flowables, one after the other,
     *         without interleaving them
     * @see <a href="http://reactivex.io/documentation/operators/concat.html">ReactiveX operators documentation: Concat</a>
     */
    public final Flowable<T> concatWith(Flowable<? extends T> t1) {
        return concat(this, t1);
    }

    /**
     * Returns an Flowable that emits a Boolean that indicates whether the source Flowable emitted a
     * specified item.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/contains.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code contains} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param element
     *            the item to search for in the emissions from the source Flowable
     * @return an Flowable that emits {@code true} if the specified item is emitted by the source Flowable,
     *         or {@code false} if the source Flowable completes without emitting that item
     * @see <a href="http://reactivex.io/documentation/operators/contains.html">ReactiveX operators documentation: Contains</a>
     */
    public final Flowable<Boolean> contains(final Object element) {
        return exists(t1 -> element == null ? t1 == null : element.equals(t1));
    }

    /**
     * Returns an Flowable that counts the total number of items emitted by the source Flowable and emits
     * this count as a 64-bit Long.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/longCount.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because by intent it will receive all values and reduce
     *      them to a single {@code onNext}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code countLong} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits a single item: the number of items emitted by the source Flowable as a
     *         64-bit Long item
     * @see <a href="http://reactivex.io/documentation/operators/count.html">ReactiveX operators documentation: Count</a>
     * @see #count()
     */
    public final Flowable<Long> countLong() {
        return reduce(0L, (t1, t2) -> t1 + 1);
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, except that it drops items emitted by the
     * source Flowable that are followed by another item within a computed debounce duration.
     * <p>
     * <img width="640" height="425" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/debounce.f.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses the {@code debounceSelector} to mark
     *      boundaries.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code debounce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the debounce value type (ignored)
     * @param debounceSelector
     *            function to retrieve a sequence that indicates the throttle duration for each item
     * @return an Flowable that omits items emitted by the source Flowable that are followed by another item
     *         within a computed debounce duration
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     */
    public final <U> Flowable<T> debounce(Function<? super T, ? extends Flowable<U>> debounceSelector) {
        return lift(new OperatorDebounceWithSelector<T, U>(debounceSelector));
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, except that it drops items emitted by the
     * source Flowable that are followed by newer items before a timeout value expires. The timer resets on
     * each emission.
     * <p>
     * <em>Note:</em> If items keep being emitted by the source Flowable faster than the timeout then no items
     * will be emitted by the resulting Flowable.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/debounce.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code debounce} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            the time each item has to be "the most recent" of those emitted by the source Flowable to
     *            ensure that it's not dropped
     * @param unit
     *            the {@link TimeUnit} for the timeout
     * @return an Flowable that filters out items from the source Flowable that are too quickly followed by
     *         newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #throttleWithTimeout(long, TimeUnit)
     */
    public final Flowable<T> debounce(long timeout, TimeUnit unit) {
        return debounce(timeout, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, except that it drops items emitted by the
     * source Flowable that are followed by newer items before a timeout value expires on a specified
     * Scheduler. The timer resets on each emission.
     * <p>
     * <em>Note:</em> If items keep being emitted by the source Flowable faster than the timeout then no items
     * will be emitted by the resulting Flowable.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/debounce.s.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            the time each item has to be "the most recent" of those emitted by the source Flowable to
     *            ensure that it's not dropped
     * @param unit
     *            the unit of time for the specified timeout
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle the timeout for each
     *            item
     * @return an Flowable that filters out items from the source Flowable that are too quickly followed by
     *         newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #throttleWithTimeout(long, TimeUnit, Scheduler)
     */
    public final Flowable<T> debounce(long timeout, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorDebounceWithTime<T>(timeout, unit, scheduler));
    }

    /**
     * Returns an Flowable that emits the items emitted by the source Flowable or a specified default item
     * if the source Flowable is empty.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/defaultIfEmpty.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code defaultIfEmpty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            the item to emit if the source Flowable emits no items
     * @return an Flowable that emits either the specified default item if the source Flowable emits no
     *         items, or the items emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/defaultifempty.html">ReactiveX operators documentation: DefaultIfEmpty</a>
     */
    public final Flowable<T> defaultIfEmpty(T defaultValue) {
        return lift(new OperatorDefaultIfEmpty<T>(defaultValue));
    }

    /**
     * Returns an Flowable that emits the items emitted by the source Flowable or the items of an alternate
     * Flowable if the source Flowable is empty.
     * <p/>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchIfEmpty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param alternate
     *              the alternate Flowable to subscribe to if the source does not emit any items
     * @return  an Flowable that emits the items emitted by the source Flowable or the items of an
     *          alternate Flowable if the source Flowable is empty.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final Flowable<T> switchIfEmpty(Flowable<? extends T> alternate) {
        return lift(new OperatorSwitchIfEmpty<T>(alternate));
    }

    /**
     * Returns an Flowable that delays the subscription to and emissions from the souce Flowable via another
     * Flowable on a per-item basis.
     * <p>
     * <img width="640" height="450" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.oo.png" alt="">
     * <p>
     * <em>Note:</em> the resulting Flowable will immediately propagate any {@code onError} notification
     * from the source Flowable.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the subscription delay value type (ignored)
     * @param <V>
     *            the item delay value type (ignored)
     * @param subscriptionDelay
     *            a function that returns an Flowable that triggers the subscription to the source Flowable
     *            once it emits any item
     * @param itemDelay
     *            a function that returns an Flowable for each item emitted by the source Flowable, which is
     *            then used to delay the emission of that item by the resulting Flowable until the Flowable
     *            returned from {@code itemDelay} emits an item
     * @return an Flowable that delays the subscription and emissions of the source Flowable via another
     *         Flowable on a per-item basis
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    public final <U, V> Flowable<T> delay(
            Supplier<? extends Flowable<U>> subscriptionDelay,
            Function<? super T, ? extends Flowable<V>> itemDelay) {
        return delaySubscription(subscriptionDelay).lift(new OperatorDelayWithSelector<T, V>(this, itemDelay));
    }

    /**
     * Returns an Flowable that delays the emissions of the source Flowable via another Flowable on a
     * per-item basis.
     * <p>
     * <img width="640" height="450" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.o.png" alt="">
     * <p>
     * <em>Note:</em> the resulting Flowable will immediately propagate any {@code onError} notification
     * from the source Flowable.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the item delay value type (ignored)
     * @param itemDelay
     *            a function that returns an Flowable for each item emitted by the source Flowable, which is
     *            then used to delay the emission of that item by the resulting Flowable until the Flowable
     *            returned from {@code itemDelay} emits an item
     * @return an Flowable that delays the emissions of the source Flowable via another Flowable on a
     *         per-item basis
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    public final <U> Flowable<T> delay(Function<? super T, ? extends Flowable<U>> itemDelay) {
        return lift(new OperatorDelayWithSelector<T, U>(this, itemDelay));
    }

    /**
     * Returns an Flowable that emits the items emitted by the source Flowable shifted forward in time by a
     * specified delay. Error notifications from the source Flowable are not delayed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} operates by default on the {@code compuation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param delay
     *            the delay to shift the source by
     * @param unit
     *            the {@link TimeUnit} in which {@code period} is defined
     * @return the source Flowable shifted in time by the specified delay
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    public final Flowable<T> delay(long delay, TimeUnit unit) {
        return delay(delay, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits the items emitted by the source Flowable shifted forward in time by a
     * specified delay. Error notifications from the source Flowable are not delayed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delay.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param delay
     *            the delay to shift the source by
     * @param unit
     *            the time unit of {@code delay}
     * @param scheduler
     *            the {@link Scheduler} to use for delaying
     * @return the source Flowable shifted in time by the specified delay
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    public final Flowable<T> delay(long delay, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorDelay<T>(this, delay, unit, scheduler));
    }

    /**
     * Returns an Flowable that delays the subscription to the source Flowable by a given amount of time.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delaySubscription.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} operates by default on the {@code compuation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param delay
     *            the time to delay the subscription
     * @param unit
     *            the time unit of {@code delay}
     * @return an Flowable that delays the subscription to the source Flowable by the given amount
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    public final Flowable<T> delaySubscription(long delay, TimeUnit unit) {
        return delaySubscription(delay, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that delays the subscription to the source Flowable by a given amount of time,
     * both waiting and subscribing on a given Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delaySubscription.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param delay
     *            the time to delay the subscription
     * @param unit
     *            the time unit of {@code delay}
     * @param scheduler
     *            the Scheduler on which the waiting and subscription will happen
     * @return an Flowable that delays the subscription to the source Flowable by a given
     *         amount, waiting and subscribing on the given Scheduler
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    public final Flowable<T> delaySubscription(long delay, TimeUnit unit, Scheduler scheduler) {
        return create(new OnSubscribeDelaySubscription<T>(this, delay, unit, scheduler));
    }
    
    /**
     * Returns an Flowable that delays the subscription to the source Flowable until a second Flowable
     * emits an item.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/delaySubscription.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code delay} operates by default on the {@code compuation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param subscriptionDelay
     *            a function that returns an Flowable that triggers the subscription to the source Flowable
     *            once it emits any item
     * @return an Flowable that delays the subscription to the source Flowable until the Flowable returned
     *         by {@code subscriptionDelay} emits an item
     * @see <a href="http://reactivex.io/documentation/operators/delay.html">ReactiveX operators documentation: Delay</a>
     */
    public final <U> Flowable<T> delaySubscription(Supplier<? extends Flowable<U>> subscriptionDelay) {
        return create(new OnSubscribeDelaySubscriptionWithSelector<T, U>(this, subscriptionDelay));
    }

    /**
     * Returns an Flowable that reverses the effect of {@link #materialize materialize} by transforming the
     * {@link Notification} objects emitted by the source Flowable into the items or notifications they
     * represent.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/dematerialize.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code dematerialize} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits the items and notifications embedded in the {@link Notification} objects
     *         emitted by the source Flowable
     * @throws OnErrorNotImplementedException
     *             if the source Flowable is not of type {@code Flowable<Notification<T>>}
     * @see <a href="http://reactivex.io/documentation/operators/materialize-dematerialize.html">ReactiveX operators documentation: Dematerialize</a>
     */
    @SuppressWarnings({"unchecked"})
    public final <T2> Flowable<T2> dematerialize() {
        return lift(OperatorDematerialize.instance());
    }

    /**
     * Returns an Flowable that emits all items emitted by the source Flowable that are distinct.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinct.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinct} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits only those items emitted by the source Flowable that are distinct from
     *         each other
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    public final Flowable<T> distinct() {
        return lift(new OperatorDistinct<T, T>(UtilityFunctions.<T>identity()));
    }

    /**
     * Returns an Flowable that emits all items emitted by the source Flowable that are distinct according
     * to a key selector function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinct.key.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinct} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that projects an emitted item to a key value that is used to decide whether an item
     *            is distinct from another one or not
     * @return an Flowable that emits those items emitted by the source Flowable that have distinct keys
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    public final <U> Flowable<T> distinct(Function<? super T, ? extends U> keySelector) {
        return lift(new OperatorDistinct<T, U>(keySelector));
    }

    /**
     * Returns an Flowable that emits all items emitted by the source Flowable that are distinct from their
     * immediate predecessors.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinctUntilChanged.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinctUntilChanged} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits those items from the source Flowable that are distinct from their
     *         immediate predecessors
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    public final Flowable<T> distinctUntilChanged() {
        return lift(new OperatorDistinctUntilChanged<T, T>(UtilityFunctions.<T>identity()));
    }

    /**
     * Returns an Flowable that emits all items emitted by the source Flowable that are distinct from their
     * immediate predecessors, according to a key selector function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/distinctUntilChanged.key.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code distinctUntilChanged} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that projects an emitted item to a key value that is used to decide whether an item
     *            is distinct from another one or not
     * @return an Flowable that emits those items from the source Flowable whose keys are distinct from
     *         those of their immediate predecessors
     * @see <a href="http://reactivex.io/documentation/operators/distinct.html">ReactiveX operators documentation: Distinct</a>
     */
    public final <U> Flowable<T> distinctUntilChanged(Function<? super T, ? extends U> keySelector) {
        return lift(new OperatorDistinctUntilChanged<T, U>(keySelector));
    }

    /**
     * Modifies the source {@code Flowable} so that it invokes the given action when it receives a request for
     * more items. 
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnRequest} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param onRequest
     *            the action that gets called when an observer requests items from this {@code Flowable}
     * @return the source {@code Flowable} modified so as to call this Action when appropriate
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Beta
    public final Flowable<T> doOnRequest(final Consumer<Long> onRequest) {
        return lift(new OperatorDoOnRequest<T>(onRequest));
    }

    /**
     * Modifies the source {@code Flowable} so that it invokes the given action when it is subscribed from
     * its subscribers. Each subscription will result in an invocation of the given action except when the
     * source {@code Flowable} is reference counted, in which case the source {@code Flowable} will invoke
     * the given action for the first subscription.
     * <p>
     * <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnSubscribe.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnSubscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param subscribe
     *            the action that gets called when an observer subscribes to this {@code Flowable}
     * @return the source {@code Flowable} modified so as to call this Action when appropriate
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    public final Flowable<T> doOnSubscribe(final Runnable subscribe) {
        return lift(new OperatorDoOnSubscribe<T>(subscribe));
    }
    
    /**
     * Modifies the source Flowable so that it invokes an action when it calls {@code onCompleted} or
     * {@code onError}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnTerminate.png" alt="">
     * <p>
     * This differs from {@code finallyDo} in that this happens <em>before</em> the {@code onCompleted} or
     * {@code onError} notification.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnTerminate} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onTerminate
     *            the action to invoke when the source Flowable calls {@code onCompleted} or {@code onError}
     * @return the source Flowable with the side-effecting behavior applied
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     * @see #finallyDo(Runnable)
     */
    public final Flowable<T> doOnTerminate(final Runnable onTerminate) {
        Subscriber<T> observer = new Subscriber<T>() {
            @Override
            public final void onCompleted() {
                onTerminate.call();
            }

            @Override
            public final void onError(Throwable e) {
                onTerminate.call();
            }

            @Override
            public final void onNext(T args) {
            }

        };

        return lift(new OperatorDoOnEach<T>(observer));
    }
    
    /**
     * Modifies the source {@code Flowable} so that it invokes the given action when it is unsubscribed from
     * its subscribers. Each un-subscription will result in an invocation of the given action except when the
     * source {@code Flowable} is reference counted, in which case the source {@code Flowable} will invoke
     * the given action for the very last un-subscription.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/doOnUnsubscribe.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code doOnUnsubscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param unsubscribe
     *            the action that gets called when this {@code Flowable} is unsubscribed
     * @return the source {@code Flowable} modified so as to call this Action when appropriate
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     */
    public final Flowable<T> doOnUnsubscribe(final Runnable unsubscribe) {
        return lift(new OperatorDoOnUnsubscribe<T>(unsubscribe));
    }

    /**
     * Returns an Flowable that emits the single item at a specified index in a sequence of emissions from a
     * source Observbable.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/elementAt.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code elementAt} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param index
     *            the zero-based index of the item to retrieve
     * @return an Flowable that emits a single item: the item at the specified position in the sequence of
     *         those emitted by the source Flowable
     * @throws IndexOutOfBoundsException
     *             if {@code index} is greater than or equal to the number of items emitted by the source
     *             Flowable, or
     *             if {@code index} is less than 0
     * @see <a href="http://reactivex.io/documentation/operators/elementat.html">ReactiveX operators documentation: ElementAt</a>
     */
    public final Flowable<T> elementAt(int index) {
        return lift(new OperatorElementAt<T>(index));
    }

    /**
     * Returns an Flowable that emits the item found at a specified index in a sequence of emissions from a
     * source Flowable, or a default item if that index is out of range.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/elementAtOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code elementAtOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param index
     *            the zero-based index of the item to retrieve
     * @param defaultValue
     *            the default item
     * @return an Flowable that emits the item at the specified position in the sequence emitted by the source
     *         Flowable, or the default item if that index is outside the bounds of the source sequence
     * @throws IndexOutOfBoundsException
     *             if {@code index} is less than 0
     * @see <a href="http://reactivex.io/documentation/operators/elementat.html">ReactiveX operators documentation: ElementAt</a>
     */
    public final Flowable<T> elementAtOrDefault(int index, T defaultValue) {
        return lift(new OperatorElementAt<T>(index, defaultValue));
    }

    /**
     * Returns an Flowable that emits {@code true} if any item emitted by the source Flowable satisfies a
     * specified condition, otherwise {@code false}. <em>Note:</em> this always emits {@code false} if the
     * source Flowable is empty.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/exists.png" alt="">
     * <p>
     * In Rx.Net this is the {@code any} Subscriber but we renamed it in RxJava to better match Java naming
     * idioms.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code exists} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            the condition to test items emitted by the source Flowable
     * @return an Flowable that emits a Boolean that indicates whether any item emitted by the source
     *         Flowable satisfies the {@code predicate}
     * @see <a href="http://reactivex.io/documentation/operators/contains.html">ReactiveX operators documentation: Contains</a>
     */
    public final Flowable<Boolean> exists(Predicate<? super T> predicate) {
        return lift(new OperatorAny<T>(predicate, false));
    }

    /**
     * Registers an {@link Runnable} to be called when this Flowable invokes either
     * {@link Subscriber#onCompleted onCompleted} or {@link Subscriber#onError onError}.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/finallyDo.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code finallyDo} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param action
     *            an {@link Runnable} to be invoked when the source Flowable finishes
     * @return an Flowable that emits the same items as the source Flowable, then invokes the
     *         {@link Runnable}
     * @see <a href="http://reactivex.io/documentation/operators/do.html">ReactiveX operators documentation: Do</a>
     * @see #doOnTerminate(Runnable)
     */
    public final Flowable<T> finallyDo(Runnable action) {
        return lift(new OperatorFinally<T>(action));
    }

    /**
     * Returns an Flowable that emits only the very first item emitted by the source Flowable, or notifies
     * of an {@code NoSuchElementException} if the source Flowable is empty.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/first.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code first} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits only the very first item emitted by the source Flowable, or raises an
     *         {@code NoSuchElementException} if the source Flowable is empty
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> first() {
        return take(1).single();
    }

    /**
     * Returns an Flowable that emits only the very first item emitted by the source Flowable that satisfies
     * a specified condition, or notifies of an {@code NoSuchElementException} if no such items are emitted.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/firstN.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code first} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            the condition that an item emitted by the source Flowable has to satisfy
     * @return an Flowable that emits only the very first item emitted by the source Flowable that satisfies
     *         the {@code predicate}, or raises an {@code NoSuchElementException} if no such items are emitted
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> first(Predicate<? super T> predicate) {
        return takeFirst(predicate).single();
    }

    /**
     * Returns an Flowable that emits only the very first item emitted by the source Flowable, or a default
     * item if the source Flowable completes without emitting anything.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/firstOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code firstOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            the default item to emit if the source Flowable doesn't emit anything
     * @return an Flowable that emits only the very first item from the source, or a default item if the
     *         source Flowable completes without emitting any items
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> firstOrDefault(T defaultValue) {
        return take(1).singleOrDefault(defaultValue);
    }

    /**
     * Returns an Flowable that emits only the very first item emitted by the source Flowable that satisfies
     * a specified condition, or a default item if the source Flowable emits no such items.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/firstOrDefaultN.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code firstOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            the condition any item emitted by the source Flowable has to satisfy
     * @param defaultValue
     *            the default item to emit if the source Flowable doesn't emit anything that satisfies the
     *            {@code predicate}
     * @return an Flowable that emits only the very first item emitted by the source Flowable that satisfies
     *         the {@code predicate}, or a default item if the source Flowable emits no such items
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> firstOrDefault(T defaultValue, Predicate<? super T> predicate) {
        return takeFirst(predicate).singleOrDefault(defaultValue);
    }

    /**
     * Returns an Flowable that applies a function to each item emitted or notification raised by the source
     * Flowable and then flattens the Flowables returned from these functions and emits the resulting items.
     * <p>
     * <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.nce.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the result type
     * @param onNext
     *            a function that returns an Flowable to merge for each item emitted by the source Flowable
     * @param onError
     *            a function that returns an Flowable to merge for an onError notification from the source
     *            Flowable
     * @param onCompleted
     *            a function that returns an Flowable to merge for an onCompleted notification from the source
     *            Flowable
     * @return an Flowable that emits the results of merging the Flowables returned from applying the
     *         specified functions to the emissions and notifications of the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <R> Flowable<R> flatMap(
            Function<? super T, ? extends Flowable<? extends R>> onNext,
            Function<? super Throwable, ? extends Flowable<? extends R>> onError,
            Supplier<? extends Flowable<? extends R>> onCompleted) {
        return merge(mapNotification(onNext, onError, onCompleted));
    }
    /**
     * Returns an Flowable that applies a function to each item emitted or notification raised by the source
     * Flowable and then flattens the Flowables returned from these functions and emits the resulting items, 
     * while limiting the maximum number of concurrent subscriptions to these Flowables.
     * <p>
     * <!-- <img width="640" height="410" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.nce.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the result type
     * @param onNext
     *            a function that returns an Flowable to merge for each item emitted by the source Flowable
     * @param onError
     *            a function that returns an Flowable to merge for an onError notification from the source
     *            Flowable
     * @param onCompleted
     *            a function that returns an Flowable to merge for an onCompleted notification from the source
     *            Flowable
     * @param maxConcurrent
     *         the maximum number of Flowables that may be subscribed to concurrently
     * @return an Flowable that emits the results of merging the Flowables returned from applying the
     *         specified functions to the emissions and notifications of the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Beta
    public final <R> Flowable<R> flatMap(
            Function<? super T, ? extends Flowable<? extends R>> onNext,
            Function<? super Throwable, ? extends Flowable<? extends R>> onError,
            Supplier<? extends Flowable<? extends R>> onCompleted, int maxConcurrent) {
        return merge(mapNotification(onNext, onError, onCompleted), maxConcurrent);
    }

    /**
     * Returns an Flowable that emits the results of a specified function to the pair of values emitted by the
     * source Flowable and a specified collection Flowable.
     * <p>
     * <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.r.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the collection Flowable
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param collectionSelector
     *            a function that returns an Flowable for each item emitted by the source Flowable
     * @param resultSelector
     *            a function that combines one item emitted by each of the source and collection Flowables and
     *            returns an item to be emitted by the resulting Flowable
     * @return an Flowable that emits the results of applying a function to a pair of values emitted by the
     *         source Flowable and the collection Flowable
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <U, R> Flowable<R> flatMap(final Function<? super T, ? extends Flowable<? extends U>> collectionSelector,
            final Func2<? super T, ? super U, ? extends R> resultSelector) {
        return merge(lift(new OperatorMapPair<T, U, R>(collectionSelector, resultSelector)));
    }
    /**
     * Returns an Flowable that emits the results of a specified function to the pair of values emitted by the
     * source Flowable and a specified collection Flowable, while limiting the maximum number of concurrent
     * subscriptions to these Flowables.
     * <p>
     * <!-- <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMap.r.png" alt=""> -->
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the type of items emitted by the collection Flowable
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param collectionSelector
     *            a function that returns an Flowable for each item emitted by the source Flowable
     * @param resultSelector
     *            a function that combines one item emitted by each of the source and collection Flowables and
     *            returns an item to be emitted by the resulting Flowable
     * @param maxConcurrent
     *         the maximum number of Flowables that may be subscribed to concurrently
     * @return an Flowable that emits the results of applying a function to a pair of values emitted by the
     *         source Flowable and the collection Flowable
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Beta
    public final <U, R> Flowable<R> flatMap(final Function<? super T, ? extends Flowable<? extends U>> collectionSelector,
            final Func2<? super T, ? super U, ? extends R> resultSelector, int maxConcurrent) {
        return merge(lift(new OperatorMapPair<T, U, R>(collectionSelector, resultSelector)), maxConcurrent);
    }

    /**
     * Returns an Flowable that merges each item emitted by the source Flowable with the values in an
     * Iterable corresponding to that item that is generated by a selector.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMapIterable.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMapIterable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of item emitted by the resulting Flowable
     * @param collectionSelector
     *            a function that returns an Iterable sequence of values for when given an item emitted by the
     *            source Flowable
     * @return an Flowable that emits the results of merging the items emitted by the source Flowable with
     *         the values in the Iterables corresponding to those items, as generated by {@code collectionSelector}
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <R> Flowable<R> flatMapIterable(Function<? super T, ? extends Iterable<? extends R>> collectionSelector) {
        return merge(map(OperatorMapPair.convertSelector(collectionSelector)));
    }

    /**
     * Returns an Flowable that emits the results of applying a function to the pair of values from the source
     * Flowable and an Iterable corresponding to that item that is generated by a selector.
     * <p>
     * <img width="640" height="390" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeMapIterable.r.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code flatMapIterable} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the collection element type
     * @param <R>
     *            the type of item emited by the resulting Flowable
     * @param collectionSelector
     *            a function that returns an Iterable sequence of values for each item emitted by the source
     *            Flowable
     * @param resultSelector
     *            a function that returns an item based on the item emitted by the source Flowable and the
     *            Iterable returned for that item by the {@code collectionSelector}
     * @return an Flowable that emits the items returned by {@code resultSelector} for each item in the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <U, R> Flowable<R> flatMapIterable(Function<? super T, ? extends Iterable<? extends U>> collectionSelector,
            Func2<? super T, ? super U, ? extends R> resultSelector) {
        return flatMap(OperatorMapPair.convertSelector(collectionSelector), resultSelector);
    }

    /**
     * Subscribes to the {@link Flowable} and receives notifications for each element.
     * <p>
     * Alias to {@link #subscribe(Consumer)}
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Consumer} to execute for each item.
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void forEach(final Consumer<? super T> onNext) {
        subscribe(onNext);
    }
    
    /**
     * Subscribes to the {@link Flowable} and receives notifications for each element and error events.
     * <p>
     * Alias to {@link #subscribe(Consumer, Consumer)}
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Consumer} to execute for each item.
     * @param onError
     *            {@link Consumer} to execute when an error is emitted.
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void forEach(final Consumer<? super T> onNext, final Consumer<Throwable> onError) {
        subscribe(onNext, onError);
    }
    
    /**
     * Subscribes to the {@link Flowable} and receives notifications for each element and the terminal events.
     * <p>
     * Alias to {@link #subscribe(Consumer, Consumer, Runnable)}
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Consumer} to execute for each item.
     * @param onError
     *            {@link Consumer} to execute when an error is emitted.
     * @param onComplete
     *            {@link Runnable} to execute when completion is signalled.
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final void forEach(final Consumer<? super T> onNext, final Consumer<Throwable> onError, final Runnable onComplete) {
        subscribe(onNext, onError, onComplete);
    }
    
    /**
     * Groups the items emitted by an {@code Flowable} according to a specified criterion, and emits these
     * grouped items as {@link GroupedFlowable}s, one {@code GroupedFlowable} per group.
     * <p>
     * <img width="640" height="360" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupBy.png" alt="">
     * <p>
     * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
     * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
     * {@code GroupedFlowable}s that do not concern you. Instead, you can signal to them that they may
     * discard their buffers by applying an operator like {@link #take}{@code (0)} to them.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupBy} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param elementSelector
     *            a function that extracts the return element for each item
     * @param <K>
     *            the key type
     * @param <R>
     *            the element type
     * @return an {@code Flowable} that emits {@link GroupedFlowable}s, each of which corresponds to a
     *         unique key value and each of which emits those items from the source Flowable that share that
     *         key value
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX operators documentation: GroupBy</a>
     */
    public final <K, R> Flowable<GroupedFlowable<K, R>> groupBy(final Function<? super T, ? extends K> keySelector, final Function<? super T, ? extends R> elementSelector) {
        return lift(new OperatorGroupBy<T, K, R>(keySelector, elementSelector));
    }
    
    /**
     * Groups the items emitted by an {@code Flowable} according to a specified criterion, and emits these
     * grouped items as {@link GroupedFlowable}s, one {@code GroupedFlowable} per group.
     * <p>
     * <img width="640" height="360" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupBy.png" alt="">
     * <p>
     * <em>Note:</em> A {@link GroupedFlowable} will cache the items it is to emit until such time as it
     * is subscribed to. For this reason, in order to avoid memory leaks, you should not simply ignore those
     * {@code GroupedFlowable}s that do not concern you. Instead, you can signal to them that they may
     * discard their buffers by applying an operator like {@link #take}{@code (0)} to them.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupBy} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            a function that extracts the key for each item
     * @param <K>
     *            the key type
     * @return an {@code Flowable} that emits {@link GroupedFlowable}s, each of which corresponds to a
     *         unique key value and each of which emits those items from the source Flowable that share that
     *         key value
     * @see <a href="http://reactivex.io/documentation/operators/groupby.html">ReactiveX operators documentation: GroupBy</a>
     */
    public final <K> Flowable<GroupedFlowable<K, T>> groupBy(final Function<? super T, ? extends K> keySelector) {
        return lift(new OperatorGroupBy<T, K, T>(keySelector));
    }

    /**
     * Returns an Flowable that correlates two Flowables when they overlap in time and groups the results.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/groupJoin.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code groupJoin} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param right
     *            the other Flowable to correlate items from the source Flowable with
     * @param leftDuration
     *            a function that returns an Flowable whose emissions indicate the duration of the values of
     *            the source Flowable
     * @param rightDuration
     *            a function that returns an Flowable whose emissions indicate the duration of the values of
     *            the {@code right} Flowable
     * @param resultSelector
     *            a function that takes an item emitted by each Flowable and returns the value to be emitted
     *            by the resulting Flowable
     * @return an Flowable that emits items based on combining those items emitted by the source Flowables
     *         whose durations overlap
     * @see <a href="http://reactivex.io/documentation/operators/join.html">ReactiveX operators documentation: Join</a>
     */
    public final <T2, D1, D2, R> Flowable<R> groupJoin(Flowable<T2> right, Function<? super T, ? extends Flowable<D1>> leftDuration,
            Function<? super T2, ? extends Flowable<D2>> rightDuration,
            Func2<? super T, ? super Flowable<T2>, ? extends R> resultSelector) {
        return create(new OnSubscribeGroupJoin<T, T2, D1, D2, R>(this, right, leftDuration, rightDuration, resultSelector));
    }

    /**
     * Ignores all items emitted by the source Flowable and only calls {@code onCompleted} or {@code onError}.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/ignoreElements.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code ignoreElements} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an empty Flowable that only calls {@code onCompleted} or {@code onError}, based on which one is
     *         called by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/ignoreelements.html">ReactiveX operators documentation: IgnoreElements</a>
     */
    public final Flowable<T> ignoreElements() {
        return filter(UtilityFunctions.alwaysFalse());
    }

    /**
     * Returns an Flowable that emits {@code true} if the source Flowable is empty, otherwise {@code false}.
     * <p>
     * In Rx.Net this is negated as the {@code any} Subscriber but we renamed this in RxJava to better match Java
     * naming idioms.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/isEmpty.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code isEmpty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits a Boolean
     * @see <a href="http://reactivex.io/documentation/operators/contains.html">ReactiveX operators documentation: Contains</a>
     */
    public final Flowable<Boolean> isEmpty() {
        return lift(new OperatorAny<T>(UtilityFunctions.alwaysTrue(), true));
    }

    /**
     * Correlates the items emitted by two Flowables based on overlapping durations.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/join_.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code join} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param right
     *            the second Flowable to join items from
     * @param leftDurationSelector
     *            a function to select a duration for each item emitted by the source Flowable, used to
     *            determine overlap
     * @param rightDurationSelector
     *            a function to select a duration for each item emitted by the {@code right} Flowable, used to
     *            determine overlap
     * @param resultSelector
     *            a function that computes an item to be emitted by the resulting Flowable for any two
     *            overlapping items emitted by the two Flowables
     * @return an Flowable that emits items correlating to items emitted by the source Flowables that have
     *         overlapping durations
     * @see <a href="http://reactivex.io/documentation/operators/join.html">ReactiveX operators documentation: Join</a>
     */
    public final <TRight, TLeftDuration, TRightDuration, R> Flowable<R> join(Flowable<TRight> right, Function<T, Flowable<TLeftDuration>> leftDurationSelector,
            Function<TRight, Flowable<TRightDuration>> rightDurationSelector,
            Func2<T, TRight, R> resultSelector) {
        return create(new OnSubscribeJoin<T, TRight, TLeftDuration, TRightDuration, R>(this, right, leftDurationSelector, rightDurationSelector, resultSelector));
    }

    /**
     * Returns an Flowable that emits only the last item emitted by the source Flowable that satisfies a
     * given condition, or notifies of a {@code NoSuchElementException} if no such items are emitted.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/last.p.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code last} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            the condition any source emitted item has to satisfy
     * @return an Flowable that emits only the last item satisfying the given condition from the source, or an
     *         {@code NoSuchElementException} if no such items are emitted
     * @throws IllegalArgumentException
     *             if no items that match the predicate are emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/last.html">ReactiveX operators documentation: Last</a>
     */
    public final Flowable<T> last(Predicate<? super T> predicate) {
        return filter(predicate).takeLast(1).single();
    }

    /**
     * Returns an Flowable that emits only the last item emitted by the source Flowable, or a default item
     * if the source Flowable completes without emitting any items.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/lastOrDefault.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code lastOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            the default item to emit if the source Flowable is empty
     * @return an Flowable that emits only the last item emitted by the source Flowable, or a default item
     *         if the source Flowable is empty
     * @see <a href="http://reactivex.io/documentation/operators/last.html">ReactiveX operators documentation: Last</a>
     */
    public final Flowable<T> lastOrDefault(T defaultValue) {
        return takeLast(1).singleOrDefault(defaultValue);
    }

    /**
     * Returns an Flowable that emits only the last item emitted by the source Flowable that satisfies a
     * specified condition, or a default item if no such item is emitted by the source Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/lastOrDefault.p.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code lastOrDefault} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param defaultValue
     *            the default item to emit if the source Flowable doesn't emit anything that satisfies the
     *            specified {@code predicate}
     * @param predicate
     *            the condition any item emitted by the source Flowable has to satisfy
     * @return an Flowable that emits only the last item emitted by the source Flowable that satisfies the
     *         given condition, or a default item if no such item is emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/last.html">ReactiveX operators documentation: Last</a>
     */
    public final Flowable<T> lastOrDefault(T defaultValue, Predicate<? super T> predicate) {
        return filter(predicate).takeLast(1).singleOrDefault(defaultValue);
    }

    /**
     * Returns an Flowable that emits only the first {@code num} items emitted by the source Flowable.
     * <p>
     * Alias of {@link #take(int)} to match Java 8 Stream API naming convention.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.png" alt="">
     * <p>
     * This method returns an Flowable that will invoke a subscribing {@link Subscriber}'s
     * {@link Subscriber#onNext onNext} function a maximum of {@code num} times before invoking
     * {@link Subscriber#onCompleted onCompleted}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code limit} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param num
     *            the maximum number of items to emit
     * @return an Flowable that emits only the first {@code num} items emitted by the source Flowable, or
     *         all of the items from the source Flowable if that Flowable emits fewer than {@code num} items
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    public final Flowable<T> limit(int num) {
        return take(num);
    }
    
    private final <R> Flowable<R> mapNotification(Function<? super T, ? extends R> onNext, Function<? super Throwable, ? extends R> onError, Supplier<? extends R> onCompleted) {
        return lift(new OperatorMapNotification<T, R>(onNext, onError, onCompleted));
    }

    /**
     * Returns an Flowable that represents all of the emissions <em>and</em> notifications from the source
     * Flowable into emissions marked with their original types within {@link Notification} objects.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/materialize.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code materialize} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits items that are the result of materializing the items and notifications
     *         of the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/materialize-dematerialize.html">ReactiveX operators documentation: Materialize</a>
     */
    public final Flowable<Notification<T>> materialize() {
        return lift(OperatorMaterialize.<T>instance());
    }

    /**
     * Flattens this and another Flowable into a single Flowable, without any transformation.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/merge.png" alt="">
     * <p>
     * You can combine items emitted by multiple Flowables so that they appear as a single Flowable, by
     * using the {@code mergeWith} method.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code mergeWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            an Flowable to be merged
     * @return an Flowable that emits all of the items emitted by the source Flowables
     * @see <a href="http://reactivex.io/documentation/operators/merge.html">ReactiveX operators documentation: Merge</a>
     */
    public final Flowable<T> mergeWith(Flowable<? extends T> t1) {
        return merge(this, t1);
    }

    /**
     * Filters the items emitted by an Flowable, only emitting those of the specified type.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/ofClass.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code ofType} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param klass
     *            the class type to filter the items emitted by the source Flowable
     * @return an Flowable that emits items from the source Flowable of type {@code klass}
     * @see <a href="http://reactivex.io/documentation/operators/filter.html">ReactiveX operators documentation: Filter</a>
     */
    public final <R> Flowable<R> ofType(final Class<R> klass) {
        return filter(klass::isInstance).cast(klass);
    }

    /**
     * Instructs an Flowable that is emitting items faster than its observer can consume them to buffer these
     * items indefinitely until they can be emitted.
     * <p>
     * <img width="640" height="300" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/bp.obp.buffer.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onBackpressureBuffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return the source Flowable modified to buffer items to the extent system resources allow
     * @see <a href="http://reactivex.io/documentation/operators/backpressure.html">ReactiveX operators documentation: backpressure operators</a>
     */
    public final Flowable<T> onBackpressureBuffer() {
        return lift(new OperatorOnBackpressureBuffer<T>());
    }

    /**
     * Instructs an Flowable that is emitting items faster than its observer can consume them to buffer up to
     * a given amount of items until they can be emitted. The resulting Flowable will {@code onError} emitting
     * a {@code BufferOverflowException} as soon as the buffer's capacity is exceeded, dropping all undelivered
     * items, and unsubscribing from the source.
     * <p>
     * <img width="640" height="300" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/bp.obp.buffer.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onBackpressureBuffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return the source Flowable modified to buffer items up to the given capacity
     * @see <a href="http://reactivex.io/documentation/operators/backpressure.html">ReactiveX operators documentation: backpressure operators</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Beta
    public final Flowable<T> onBackpressureBuffer(long capacity) {
        return lift(new OperatorOnBackpressureBuffer<T>(capacity));
    }

    /**
     * Instructs an Flowable that is emitting items faster than its observer can consume them to buffer up to
     * a given amount of items until they can be emitted. The resulting Flowable will {@code onError} emitting
     * a {@code BufferOverflowException} as soon as the buffer's capacity is exceeded, dropping all undelivered
     * items, unsubscribing from the source, and notifying the producer with {@code onOverflow}.
     * <p>
     * <img width="640" height="300" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/bp.obp.buffer.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onBackpressureBuffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return the source Flowable modified to buffer items up to the given capacity
     * @see <a href="http://reactivex.io/documentation/operators/backpressure.html">ReactiveX operators documentation: backpressure operators</a>
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Beta
    public final Flowable<T> onBackpressureBuffer(long capacity, Runnable onOverflow) {
        return lift(new OperatorOnBackpressureBuffer<T>(capacity, onOverflow));
    }

    /**
     * Instructs an Flowable that is emitting items faster than its observer can consume them to discard,
     * rather than emit, those items that its observer is not prepared to observe.
     * <p>
     * <img width="640" height="245" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/bp.obp.drop.png" alt="">
     * <p>
     * If the downstream request count hits 0 then the Flowable will refrain from calling {@code onNext} until
     * the observer invokes {@code request(n)} again to increase the request count.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onBackpressureDrop} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return the source Flowable modified to drop {@code onNext} notifications on overflow
     * @see <a href="http://reactivex.io/documentation/operators/backpressure.html">ReactiveX operators documentation: backpressure operators</a>
     */
    public final Flowable<T> onBackpressureDrop() {
        return lift(OperatorOnBackpressureDrop.<T>instance());
    }
    
    /**
     * Instructs an Flowable that is emitting items faster than its observer can consume them to
     * block the producer thread.
     * <p>
     * <img width="640" height="245" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/bp.obp.block.png" alt="">
     * <p>
     * The producer side can emit up to {@code maxQueueLength} onNext elements without blocking, but the
     * consumer side considers the amount its downstream requested through {@code Producer.request(n)}
     * and doesn't emit more than requested even if more is available. For example, using 
     * {@code onBackpressureBlock(384).observeOn(Schedulers.io())} will not throw a MissingBackpressureException.
     * <p>
     * Note that if the upstream Flowable does support backpressure, this operator ignores that capability
     * and doesn't propagate any backpressure requests from downstream.
     *  
     * @param maxQueueLength the maximum number of items the producer can emit without blocking
     * @return the source Flowable modified to block {@code onNext} notifications on overflow
     * @see <a href="http://reactivex.io/documentation/operators/backpressure.html">ReactiveX operators documentation: backpressure operators</a>
     * @Experimental The behavior of this can change at any time. 
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final Flowable<T> onBackpressureBlock(int maxQueueLength) {
        return lift(new OperatorOnBackpressureBlock<T>(maxQueueLength));
    }
    /**
     * Instructs an Flowable that is emitting items faster than its observer can consume them to block the
     * producer thread if the number of undelivered onNext events reaches the system-wide ring buffer size.
     * <p>
     * <img width="640" height="245" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/bp.obp.block.png" alt="">
     * <p>
     * The producer side can emit up to the system-wide ring buffer size onNext elements without blocking, but
     * the consumer side considers the amount its downstream requested through {@code Producer.request(n)}
     * and doesn't emit more than requested even if available.
     * <p>
     * Note that if the upstream Flowable does support backpressure, this operator ignores that capability
     * and doesn't propagate any backpressure requests from downstream.
     * 
     * @return the source Flowable modified to block {@code onNext} notifications on overflow
     * @see <a href="http://reactivex.io/documentation/operators/backpressure.html">ReactiveX operators documentation: backpressure operators</a>
     * @Experimental The behavior of this can change at any time. 
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final Flowable<T> onBackpressureBlock() {
        return onBackpressureBlock(rx.internal.util.RxRingBuffer.SIZE);
    }
    
    /**
     * Instructs an Flowable to pass control to another Flowable rather than invoking
     * {@link Subscriber#onError onError} if it encounters an error.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onErrorResumeNext.png" alt="">
     * <p>
     * By default, when an Flowable encounters an error that prevents it from emitting the expected item to
     * its {@link Subscriber}, the Flowable invokes its Subscriber's {@code onError} method, and then quits
     * without invoking any more of its Subscriber's methods. The {@code onErrorResumeNext} method changes this
     * behavior. If you pass a function that returns an Flowable ({@code resumeFunction}) to
     * {@code onErrorResumeNext}, if the original Flowable encounters an error, instead of invoking its
     * Subscriber's {@code onError} method, it will instead relinquish control to the Flowable returned from
     * {@code resumeFunction}, which will invoke the Subscriber's {@link Subscriber#onNext onNext} method if it is
     * able to do so. In such a case, because no Flowable necessarily invokes {@code onError}, the Subscriber
     * may never know that an error happened.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorResumeNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param resumeFunction
     *            a function that returns an Flowable that will take over if the source Flowable encounters
     *            an error
     * @return the original Flowable, with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    public final Flowable<T> onErrorResumeNext(final Function<Throwable, ? extends Flowable<? extends T>> resumeFunction) {
        return lift(new OperatorOnErrorResumeNextViaFunction<T>(resumeFunction));
    }

    /**
     * Instructs an Flowable to pass control to another Flowable rather than invoking
     * {@link Subscriber#onError onError} if it encounters an error.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onErrorResumeNext.png" alt="">
     * <p>
     * By default, when an Flowable encounters an error that prevents it from emitting the expected item to
     * its {@link Subscriber}, the Flowable invokes its Subscriber's {@code onError} method, and then quits
     * without invoking any more of its Subscriber's methods. The {@code onErrorResumeNext} method changes this
     * behavior. If you pass another Flowable ({@code resumeSequence}) to an Flowable's
     * {@code onErrorResumeNext} method, if the original Flowable encounters an error, instead of invoking its
     * Subscriber's {@code onError} method, it will instead relinquish control to {@code resumeSequence} which
     * will invoke the Subscriber's {@link Subscriber#onNext onNext} method if it is able to do so. In such a case,
     * because no Flowable necessarily invokes {@code onError}, the Subscriber may never know that an error
     * happened.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorResumeNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param resumeSequence
     *            a function that returns an Flowable that will take over if the source Flowable encounters
     *            an error
     * @return the original Flowable, with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    public final Flowable<T> onErrorResumeNext(final Flowable<? extends T> resumeSequence) {
        return lift(new OperatorOnErrorResumeNextViaFlowable<T>(resumeSequence));
    }

    /**
     * Instructs an Flowable to emit an item (returned by a specified function) rather than invoking
     * {@link Subscriber#onError onError} if it encounters an error.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onErrorReturn.png" alt="">
     * <p>
     * By default, when an Flowable encounters an error that prevents it from emitting the expected item to
     * its {@link Subscriber}, the Flowable invokes its Subscriber's {@code onError} method, and then quits
     * without invoking any more of its Subscriber's methods. The {@code onErrorReturn} method changes this
     * behavior. If you pass a function ({@code resumeFunction}) to an Flowable's {@code onErrorReturn}
     * method, if the original Flowable encounters an error, instead of invoking its Subscriber's
     * {@code onError} method, it will instead emit the return value of {@code resumeFunction}.
     * <p>
     * You can use this to prevent errors from propagating or to supply fallback data should errors be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorReturn} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param resumeFunction
     *            a function that returns an item that the new Flowable will emit if the source Flowable
     *            encounters an error
     * @return the original Flowable with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    public final Flowable<T> onErrorReturn(Function<Throwable, ? extends T> resumeFunction) {
        return lift(new OperatorOnErrorReturn<T>(resumeFunction));
    }

    /**
     * Instructs an Flowable to pass control to another Flowable rather than invoking
     * {@link Subscriber#onError onError} if it encounters an {@link java.lang.Exception}.
     * <p>
     * This differs from {@link #onErrorResumeNext} in that this one does not handle {@link java.lang.Throwable}
     * or {@link java.lang.Error} but lets those continue through.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/onExceptionResumeNextViaFlowable.png" alt="">
     * <p>
     * By default, when an Flowable encounters an exception that prevents it from emitting the expected item
     * to its {@link Subscriber}, the Flowable invokes its Subscriber's {@code onError} method, and then quits
     * without invoking any more of its Subscriber's methods. The {@code onExceptionResumeNext} method changes
     * this behavior. If you pass another Flowable ({@code resumeSequence}) to an Flowable's
     * {@code onExceptionResumeNext} method, if the original Flowable encounters an exception, instead of
     * invoking its Subscriber's {@code onError} method, it will instead relinquish control to
     * {@code resumeSequence} which will invoke the Subscriber's {@link Subscriber#onNext onNext} method if it is
     * able to do so. In such a case, because no Flowable necessarily invokes {@code onError}, the Subscriber
     * may never know that an exception happened.
     * <p>
     * You can use this to prevent exceptions from propagating or to supply fallback data should exceptions be
     * encountered.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code onErrorResumeNext} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param resumeSequence
     *            a function that returns an Flowable that will take over if the source Flowable encounters
     *            an exception
     * @return the original Flowable, with appropriately modified behavior
     * @see <a href="http://reactivex.io/documentation/operators/catch.html">ReactiveX operators documentation: Catch</a>
     */
    public final Flowable<T> onExceptionResumeNext(final Flowable<? extends T> resumeSequence) {
        return lift(new OperatorOnExceptionResumeNextViaFlowable<T>(resumeSequence));
    }

    /**
     * Returns a {@link ConnectableFlowable}, which is a variety of Flowable that waits until its
     * {@link ConnectableFlowable#connect connect} method is called before it begins emitting items to those
     * {@link Subscriber}s that have subscribed to it.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link ConnectableFlowable} that upon connection causes the source Flowable to emit items
     *         to its {@link Subscriber}s
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    public final ConnectableFlowable<T> publish() {
        return OperatorPublish.create(this);
    }

    /**
     * Returns an Flowable that emits the results of invoking a specified selector on items emitted by a
     * {@link ConnectableFlowable} that shares a single subscription to the underlying sequence.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            a function that can use the multicasted source sequence as many times as needed, without
     *            causing multiple subscriptions to the source sequence. Subscribers to the given source will
     *            receive all notifications of the source from the time of the subscription forward.
     * @return an Flowable that emits the results of invoking the selector on the items emitted by a {@link ConnectableFlowable} that shares a single subscription to the underlying sequence
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    public final <R> Flowable<R> publish(Function<? super Flowable<T>, ? extends Flowable<R>> selector) {
        return OperatorPublish.create(this, selector);
    }

    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable, then feeds the result of that function along with the second item emitted by the source
     * Flowable into the same function, and so on until all items have been emitted by the source Flowable,
     * and emits the final result from the final call to your function as its sole item.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/reduce.png" alt="">
     * <p>
     * This technique, which is called "reduce" here, is sometimes called "aggregate," "fold," "accumulate,"
     * "compress," or "inject" in other programming contexts. Groovy, for instance, has an {@code inject} method
     * that does a similar operation on lists.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because by intent it will receive all values and reduce
     *      them to a single {@code onNext}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code reduce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, whose
     *            result will be used in the next accumulator call
     * @return an Flowable that emits a single item that is the result of accumulating the items emitted by
     *         the source Flowable
     * @throws IllegalArgumentException
     *             if the source Flowable emits no items
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    public final Flowable<T> reduce(Func2<T, T, T> accumulator) {
        /*
         * Discussion and confirmation of implementation at
         * https://github.com/ReactiveX/RxJava/issues/423#issuecomment-27642532
         * 
         * It should use last() not takeLast(1) since it needs to emit an error if the sequence is empty.
         */
        return scan(accumulator).last();
    }

    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable and a specified seed value, then feeds the result of that function along with the second item
     * emitted by an Flowable into the same function, and so on until all items have been emitted by the
     * source Flowable, emitting the final result from the final call to your function as its sole item.
     * <p>
     * <img width="640" height="325" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/reduceSeed.png" alt="">
     * <p>
     * This technique, which is called "reduce" here, is sometimec called "aggregate," "fold," "accumulate,"
     * "compress," or "inject" in other programming contexts. Groovy, for instance, has an {@code inject} method
     * that does a similar operation on lists.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because by intent it will receive all values and reduce
     *      them to a single {@code onNext}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code reduce} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param initialValue
     *            the initial (seed) accumulator value
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, the
     *            result of which will be used in the next accumulator call
     * @return an Flowable that emits a single item that is the result of accumulating the output from the
     *         items emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/reduce.html">ReactiveX operators documentation: Reduce</a>
     * @see <a href="http://en.wikipedia.org/wiki/Fold_(higher-order_function)">Wikipedia: Fold (higher-order function)</a>
     */
    public final <R> Flowable<R> reduce(R initialValue, Func2<R, ? super T, R> accumulator) {
        return scan(initialValue, accumulator).takeLast(1);
    }
    
    /**
     * Returns an Flowable that repeats the sequence of items emitted by the source Flowable indefinitely.
     * <p>
     * <img width="640" height="309" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeat.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code repeat} operates by default on the {@code trampoline} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits the items emitted by the source Flowable repeatedly and in sequence
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeahttp://reactivex.io/documentation/operators/create.htmlt</a>
     */
    public final Flowable<T> repeat() {
        return OnSubscribeRedo.<T>repeat(this);
    }

    /**
     * Returns an Flowable that repeats the sequence of items emitted by the source Flowable indefinitely,
     * on a particular Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeat.os.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the Scheduler to emit the items on
     * @return an Flowable that emits the items emitted by the source Flowable repeatedly and in sequence
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    public final Flowable<T> repeat(Scheduler scheduler) {
        return OnSubscribeRedo.<T>repeat(this, scheduler);
    }

    /**
     * Returns an Flowable that repeats the sequence of items emitted by the source Flowable at most
     * {@code count} times.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeat.on.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code repeat} operates by default on the {@code trampoline} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the number of times the source Flowable items are repeated, a count of 0 will yield an empty
     *            sequence
     * @return an Flowable that repeats the sequence of items emitted by the source Flowable at most
     *         {@code count} times
     * @throws IllegalArgumentException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    public final Flowable<T> repeat(final long count) {
        return OnSubscribeRedo.<T>repeat(this, count);
    }

    /**
     * Returns an Flowable that repeats the sequence of items emitted by the source Flowable at most
     * {@code count} times, on a particular Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeat.ons.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param count
     *            the number of times the source Flowable items are repeated, a count of 0 will yield an empty
     *            sequence
     * @param scheduler
     *            the {@link Scheduler} to emit the items on
     * @return an Flowable that repeats the sequence of items emitted by the source Flowable at most
     *         {@code count} times on a particular Scheduler
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    public final Flowable<T> repeat(final long count, Scheduler scheduler) {
        return OnSubscribeRedo.<T>repeat(this, count, scheduler);
    }

    /**
     * Returns an Flowable that emits the same values as the source Flowable with the exception of an
     * {@code onCompleted}. An {@code onCompleted} notification from the source will result in the emission of
     * a {@code void} item to the Flowable provided as an argument to the {@code notificationHandler}
     * function. If that Flowable calls {@code onComplete} or {@code onError} then {@code repeatWhen} will
     * call {@code onCompleted} or {@code onError} on the child subscription. Otherwise, this Flowable will
     * resubscribe to the source Flowable, on a particular Scheduler.
     * <p>
     * <img width="640" height="430" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeatWhen.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param notificationHandler
     *            receives an Flowable of notifications with which a user can complete or error, aborting the repeat.
     * @param scheduler
     *            the {@link Scheduler} to emit the items on
     * @return the source Flowable modified with repeat logic
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    public final Flowable<T> repeatWhen(final Function<? super Flowable<? extends Void>, ? extends Flowable<?>> notificationHandler, Scheduler scheduler) {
        Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> dematerializedNotificationHandler = new Function<Flowable<? extends Notification<?>>, Flowable<?>>() {
            @Override
            public Flowable<?> call(Flowable<? extends Notification<?>> notifications) {
                return notificationHandler.call(notifications.map(new Function<Notification<?>, Void>() {
                    @Override
                    public Void call(Notification<?> notification) {
                        return null;
                    }
                }));
            }
        };
        return OnSubscribeRedo.repeat(this, dematerializedNotificationHandler, scheduler);
    }

    /**
     * Returns an Flowable that emits the same values as the source Flowable with the exception of an
     * {@code onCompleted}. An {@code onCompleted} notification from the source will result in the emission of
     * a {@code void} item to the Flowable provided as an argument to the {@code notificationHandler}
     * function. If that Flowable calls {@code onComplete} or {@code onError} then {@code repeatWhen} will
     * call {@code onCompleted} or {@code onError} on the child subscription. Otherwise, this Flowable will
     * resubscribe to the source observable.
     * <p>
     * <img width="640" height="430" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/repeatWhen.f.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code repeatWhen} operates by default on the {@code trampoline} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param notificationHandler
     *            receives an Flowable of notifications with which a user can complete or error, aborting the repeat.
     * @return the source Flowable modified with repeat logic
     * @see <a href="http://reactivex.io/documentation/operators/repeat.html">ReactiveX operators documentation: Repeat</a>
     */
    public final Flowable<T> repeatWhen(final Function<? super Flowable<? extends Void>, ? extends Flowable<?>> notificationHandler) {
        Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> dematerializedNotificationHandler = new Function<Flowable<? extends Notification<?>>, Flowable<?>>() {
            @Override
            public Flowable<?> call(Flowable<? extends Notification<?>> notifications) {
                return notificationHandler.call(notifications.map(new Function<Notification<?>, Void>() {
                    @Override
                    public Void call(Notification<?> notification) {
                        return null;
                    }
                }));
            }
        };
        return OnSubscribeRedo.repeat(this, dematerializedNotificationHandler);
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the underlying Flowable
     * that will replay all of its items and notifications to any future {@link Subscriber}. A Connectable
     * Flowable resembles an ordinary Flowable, except that it does not begin emitting items when it is
     * subscribed to, but only when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link ConnectableFlowable} that upon connection causes the source Flowable to emit its
     *         items to its {@link Subscriber}s
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay() {
        return new OperatorMulticast<T, T>(this, new Supplier<Subject<? super T, ? extends T>>() {

            @Override
            public Subject<? super T, ? extends T> call() {
                return ReplaySubject.<T> create();
            }
            
        });
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on the items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable.
     * <p>
     * <img width="640" height="450" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.f.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            the selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @return an Flowable that emits items that are the results of invoking the selector on a
     *         {@link ConnectableFlowable} that shares a single subscription to the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector) {
        return create(new OnSubscribeMulticastSelector<T, T, R>(this, new Supplier<Subject<T, T>>() {
            @Override
            public final Subject<T, T> call() {
                return ReplaySubject.create();
            }
        }, selector));
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     * replaying {@code bufferSize} notifications.
     * <p>
     * <img width="640" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fn.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            the selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable observable can replay
     * @return an Flowable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source Flowable
     *         replaying no more than {@code bufferSize} items
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector, final int bufferSize) {
        return create(new OnSubscribeMulticastSelector<T, T, R>(this, new Supplier<Subject<T, T>>() {
            @Override
            public final Subject<T, T> call() {
                return ReplaySubject.<T>createWithSize(bufferSize);
            }
        }, selector));
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     * replaying no more than {@code bufferSize} items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="445" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fnt.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable observable can replay
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source Flowable, and
     *         replays no more than {@code bufferSize} items that were emitted within the window defined by
     *         {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector, int bufferSize, long time, TimeUnit unit) {
        return replay(selector, bufferSize, time, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     * replaying no more than {@code bufferSize} items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="445" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fnts.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable observable can replay
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that is the time source for the window
     * @return an Flowable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source Flowable, and
     *         replays no more than {@code bufferSize} items that were emitted within the window defined by
     *         {@code time}
     * @throws IllegalArgumentException
     *             if {@code bufferSize} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector, final int bufferSize, final long time, final TimeUnit unit, final Scheduler scheduler) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("bufferSize < 0");
        }
        return create(new OnSubscribeMulticastSelector<T, T, R>(this, new Supplier<Subject<T, T>>() {
            @Override
            public final Subject<T, T> call() {
                return ReplaySubject.<T>createWithTimeAndSize(time, unit, bufferSize, scheduler);
            }
        }, selector));
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     * replaying a maximum of {@code bufferSize} items.
     * <p>
     * <img width="640" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fns.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @param bufferSize
     *            the buffer size that limits the number of items the connectable observable can replay
     * @param scheduler
     *            the Scheduler on which the replay is observed
     * @return an Flowable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     *         replaying no more than {@code bufferSize} notifications
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector, final int bufferSize, final Scheduler scheduler) {
        return create(new OnSubscribeMulticastSelector<T, T, R>(this, new Supplier<Subject<T, T>>() {
            @Override
            public final Subject<T, T> call() {
                return OperatorReplay.<T> createScheduledSubject(ReplaySubject.<T>createWithSize(bufferSize), scheduler);
            }
        }, selector));
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     * replaying all items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="435" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.ft.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     *         replaying all items that were emitted within the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector, long time, TimeUnit unit) {
        return replay(selector, time, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     * replaying all items that were emitted within a specified time window.
     * <p>
     * <img width="640" height="440" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fts.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler that is the time source for the window
     * @return an Flowable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     *         replaying all items that were emitted within the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector, final long time, final TimeUnit unit, final Scheduler scheduler) {
        return create(new OnSubscribeMulticastSelector<T, T, R>(this, new Supplier<Subject<T, T>>() {
            @Override
            public final Subject<T, T> call() {
                return ReplaySubject.<T>createWithTime(time, unit, scheduler);
            }
        }, selector));
    }

    /**
     * Returns an Flowable that emits items that are the results of invoking a specified selector on items
     * emitted by a {@link ConnectableFlowable} that shares a single subscription to the source Flowable.
     * <p>
     * <img width="640" height="445" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.fs.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param selector
     *            a selector function, which can use the multicasted sequence as many times as needed, without
     *            causing multiple subscriptions to the Flowable
     * @param scheduler
     *            the Scheduler where the replay is observed
     * @return an Flowable that emits items that are the results of invoking the selector on items emitted by
     *         a {@link ConnectableFlowable} that shares a single subscription to the source Flowable,
     *         replaying all items
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final <R> Flowable<R> replay(Function<? super Flowable<T>, ? extends Flowable<R>> selector, final Scheduler scheduler) {
        return create(new OnSubscribeMulticastSelector<T, T, R>(this, new Supplier<Subject<T, T>>() {
            @Override
            public final Subject<T, T> call() {
                return OperatorReplay.createScheduledSubject(ReplaySubject.<T> create(), scheduler);
            }
        }, selector));
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source Flowable that
     * replays at most {@code bufferSize} items emitted by that Flowable. A Connectable Flowable resembles
     * an ordinary Flowable, except that it does not begin emitting items when it is subscribed to, but only
     * when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.n.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     *         replays at most {@code bufferSize} items emitted by that Flowable
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay(final int bufferSize) {
        return new OperatorMulticast<T, T>(this, new Supplier<Subject<? super T, ? extends T>>() {

            @Override
            public Subject<? super T, ? extends T> call() {
                return ReplaySubject.<T>createWithSize(bufferSize);
            }
            
        });
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     * replays at most {@code bufferSize} items that were emitted during a specified time window. A Connectable
     * Flowable resembles an ordinary Flowable, except that it does not begin emitting items when it is
     * subscribed to, but only when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.nt.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     *         replays at most {@code bufferSize} items that were emitted during the window defined by
     *         {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay(int bufferSize, long time, TimeUnit unit) {
        return replay(bufferSize, time, unit, Schedulers.computation());
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     * that replays a maximum of {@code bufferSize} items that are emitted within a specified time window. A
     * Connectable Flowable resembles an ordinary Flowable, except that it does not begin emitting items
     * when it is subscribed to, but only when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.nts.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler that is used as a time source for the window
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     *         replays at most {@code bufferSize} items that were emitted during the window defined by
     *         {@code time}
     * @throws IllegalArgumentException
     *             if {@code bufferSize} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay(final int bufferSize, final long time, final TimeUnit unit, final Scheduler scheduler) {
        if (bufferSize < 0) {
            throw new IllegalArgumentException("bufferSize < 0");
        }
        return new OperatorMulticast<T, T>(this, new Supplier<Subject<? super T, ? extends T>>() {

            @Override
            public Subject<? super T, ? extends T> call() {
                return ReplaySubject.<T>createWithTimeAndSize(time, unit, bufferSize, scheduler);
            }
            
        });
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     * replays at most {@code bufferSize} items emitted by that Flowable. A Connectable Flowable resembles
     * an ordinary Flowable, except that it does not begin emitting items when it is subscribed to, but only
     * when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.ns.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param bufferSize
     *            the buffer size that limits the number of items that can be replayed
     * @param scheduler
     *            the scheduler on which the Subscribers will observe the emitted items
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     *         replays at most {@code bufferSize} items that were emitted by the Flowable
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay(final int bufferSize, final Scheduler scheduler) {
        return new OperatorMulticast<T, T>(this, new Supplier<Subject<? super T, ? extends T>>() {

            @Override
            public Subject<? super T, ? extends T> call() {
                return OperatorReplay.createScheduledSubject(ReplaySubject.<T>createWithSize(bufferSize), scheduler);
            }
            
        });
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     * replays all items emitted by that Flowable within a specified time window. A Connectable Flowable
     * resembles an ordinary Flowable, except that it does not begin emitting items when it is subscribed to,
     * but only when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.t.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code replay} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     *         replays the items that were emitted during the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay(long time, TimeUnit unit) {
        return replay(time, unit, Schedulers.computation());
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     * replays all items emitted by that Flowable within a specified time window. A Connectable Flowable
     * resembles an ordinary Flowable, except that it does not begin emitting items when it is subscribed to,
     * but only when its {@code connect} method is called. 
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.ts.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the duration of the window in which the replayed items must have been emitted
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that is the time source for the window
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source Flowable and
     *         replays the items that were emitted during the window defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay(final long time, final TimeUnit unit, final Scheduler scheduler) {
        return new OperatorMulticast<T, T>(this, new Supplier<Subject<? super T, ? extends T>>() {

            @Override
            public Subject<? super T, ? extends T> call() {
                return ReplaySubject.<T>createWithTime(time, unit, scheduler);
            }
            
        });
    }

    /**
     * Returns a {@link ConnectableFlowable} that shares a single subscription to the source Flowable that
     * will replay all of its items and notifications to any future {@link Subscriber} on the given
     * {@link Scheduler}. A Connectable Flowable resembles an ordinary Flowable, except that it does not
     * begin emitting items when it is subscribed to, but only when its {@code connect} method is called.
     * <p>
     * <img width="640" height="515" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/replay.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the Scheduler on which the Subscribers will observe the emitted items
     * @return a {@link ConnectableFlowable} that shares a single subscription to the source Flowable that
     *         will replay all of its items and notifications to any future {@link Subscriber} on the given
     *         {@link Scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/replay.html">ReactiveX operators documentation: Replay</a>
     */
    public final ConnectableFlowable<T> replay(final Scheduler scheduler) {
        return new OperatorMulticast<T, T>(this, new Supplier<Subject<? super T, ? extends T>>() {

            @Override
            public Subject<? super T, ? extends T> call() {
                return OperatorReplay.createScheduledSubject(ReplaySubject.<T> create(), scheduler);
            }
            
        });
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, resubscribing to it if it calls {@code onError}
     * (infinite retry count).
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retry.png" alt="">
     * <p>
     * If the source Flowable calls {@link Subscriber#onError}, this method will resubscribe to the source
     * Flowable rather than propagating the {@code onError} call.
     * <p>
     * Any and all items emitted by the source Flowable will be emitted by the resulting Flowable, even
     * those emitted during failed subscriptions. For example, if an Flowable fails at first but emits
     * {@code [1, 2]} then succeeds the second time and emits {@code [1, 2, 3, 4, 5]} then the complete sequence
     * of emissions and notifications would be {@code [1, 2, 1, 2, 3, 4, 5, onCompleted]}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} operates by default on the {@code trampoline} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return the source Flowable modified with retry logic
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    public final Flowable<T> retry() {
        return OnSubscribeRedo.<T>retry(this);
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, resubscribing to it if it calls {@code onError}
     * up to a specified number of retries.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retry.png" alt="">
     * <p>
     * If the source Flowable calls {@link Subscriber#onError}, this method will resubscribe to the source
     * Flowable for a maximum of {@code count} resubscriptions rather than propagating the
     * {@code onError} call.
     * <p>
     * Any and all items emitted by the source Flowable will be emitted by the resulting Flowable, even
     * those emitted during failed subscriptions. For example, if an Flowable fails at first but emits
     * {@code [1, 2]} then succeeds the second time and emits {@code [1, 2, 3, 4, 5]} then the complete sequence
     * of emissions and notifications would be {@code [1, 2, 1, 2, 3, 4, 5, onCompleted]}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} operates by default on the {@code trampoline} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            number of retry attempts before failing
     * @return the source Flowable modified with retry logic
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    public final Flowable<T> retry(final long count) {
        return OnSubscribeRedo.<T>retry(this, count);
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, resubscribing to it if it calls {@code onError}
     * and the predicate returns true for that specific exception and retry count.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retry.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retry} operates by default on the {@code trampoline} {@link Scheduler}.</dd>
     * </dl>
     *
     * @param predicate
     *            the predicate that determines if a resubscription may happen in case of a specific exception
     *            and retry count
     * @return the source Flowable modified with retry logic
     * @see #retry()
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    public final Flowable<T> retry(BiPredicate<Integer, Throwable> predicate) {
        return nest().lift(new OperatorRetryWithPredicate<T>(predicate));
    }

    /**
     * Returns an Flowable that emits the same values as the source observable with the exception of an
     * {@code onError}. An {@code onError} notification from the source will result in the emission of a
     * {@link Throwable} item to the Flowable provided as an argument to the {@code notificationHandler}
     * function. If that Flowable calls {@code onComplete} or {@code onError} then {@code retry} will call
     * {@code onCompleted} or {@code onError} on the child subscription. Otherwise, this Flowable will
     * resubscribe to the source Flowable.    
     * <p>
     * <img width="640" height="430" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retryWhen.f.png" alt="">
     * 
     * Example:
     * 
     * This retries 3 times, each time incrementing the number of seconds it waits.
     * 
     * <pre> {@code
     *  Flowable.create((Subscriber<? super String> s) -> {
     *      System.out.println("subscribing");
     *      s.onError(new RuntimeException("always fails"));
     *  }).retryWhen(attempts -> {
     *      return attempts.zipWith(Flowable.range(1, 3), (n, i) -> i).flatMap(i -> {
     *          System.out.println("delay retry by " + i + " second(s)");
     *          return Flowable.timer(i, TimeUnit.SECONDS);
     *      });
     *  }).toBlocking().forEach(System.out::println);
     * } </pre>
     * 
     * Output is:
     *
     * <pre> {@code
     * subscribing
     * delay retry by 1 second(s)
     * subscribing
     * delay retry by 2 second(s)
     * subscribing
     * delay retry by 3 second(s)
     * subscribing
     * } </pre>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code retryWhen} operates by default on the {@code trampoline} {@link Scheduler}.</dd>
     * </dl>
     *
     * @param notificationHandler
     *            receives an Flowable of notifications with which a user can complete or error, aborting the
     *            retry
     * @return the source Flowable modified with retry logic
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    public final Flowable<T> retryWhen(final Function<? super Flowable<? extends Throwable>, ? extends Flowable<?>> notificationHandler) {
        Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> dematerializedNotificationHandler = new Function<Flowable<? extends Notification<?>>, Flowable<?>>() {
            @Override
            public Flowable<?> call(Flowable<? extends Notification<?>> notifications) {
                return notificationHandler.call(notifications.map(new Function<Notification<?>, Throwable>() {
                    @Override
                    public Throwable call(Notification<?> notification) {
                        return notification.getThrowable();
                    }
                }));
            }
        };
        return OnSubscribeRedo.<T> retry(this, dematerializedNotificationHandler);
    }

    /**
     * Returns an Flowable that emits the same values as the source observable with the exception of an
     * {@code onError}. An {@code onError} will cause the emission of the {@link Throwable} that cause the
     * error to the Flowable returned from {@code notificationHandler}. If that Flowable calls
     * {@code onComplete} or {@code onError} then {@code retry} will call {@code onCompleted} or {@code onError}
     * on the child subscription. Otherwise, this Flowable will resubscribe to the source observable, on a
     * particular Scheduler.    
     * <p>
     * <img width="640" height="430" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/retryWhen.f.png" alt="">
     * <p>
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     *
     * @param notificationHandler
     *            receives an Flowable of notifications with which a user can complete or error, aborting the
     *            retry
     * @param scheduler
     *            the {@link Scheduler} on which to subscribe to the source Flowable
     * @return the source Flowable modified with retry logic
     * @see <a href="http://reactivex.io/documentation/operators/retry.html">ReactiveX operators documentation: Retry</a>
     */
    public final Flowable<T> retryWhen(final Function<? super Flowable<? extends Throwable>, ? extends Flowable<?>> notificationHandler, Scheduler scheduler) {
        Function<? super Flowable<? extends Notification<?>>, ? extends Flowable<?>> dematerializedNotificationHandler = new Function<Flowable<? extends Notification<?>>, Flowable<?>>() {
            @Override
            public Flowable<?> call(Flowable<? extends Notification<?>> notifications) {
                return notificationHandler.call(notifications.map(new Function<Notification<?>, Throwable>() {
                    @Override
                    public Throwable call(Notification<?> notification) {
                        return notification.getThrowable();
                    }
                }));
            }
        };
        return OnSubscribeRedo.<T> retry(this, dematerializedNotificationHandler, scheduler);
    }

    /**
     * Returns an Flowable that emits the most recently emitted item (if any) emitted by the source Flowable
     * within periodic time intervals.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sample.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code sample} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param period
     *            the sampling rate
     * @param unit
     *            the {@link TimeUnit} in which {@code period} is defined
     * @return an Flowable that emits the results of sampling the items emitted by the source Flowable at
     *         the specified time interval
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #throttleLast(long, TimeUnit)
     */
    public final Flowable<T> sample(long period, TimeUnit unit) {
        return sample(period, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits the most recently emitted item (if any) emitted by the source Flowable
     * within periodic time intervals, where the intervals are defined on a particular Scheduler.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sample.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param period
     *            the sampling rate
     * @param unit
     *            the {@link TimeUnit} in which {@code period} is defined
     * @param scheduler
     *            the {@link Scheduler} to use when sampling
     * @return an Flowable that emits the results of sampling the items emitted by the source Flowable at
     *         the specified time interval
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #throttleLast(long, TimeUnit, Scheduler)
     */
    public final Flowable<T> sample(long period, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorSampleWithTime<T>(period, unit, scheduler));
    }

    /**
     * Returns an Flowable that, when the specified {@code sampler} Flowable emits an item or completes,
     * emits the most recently emitted item (if any) emitted by the source Flowable since the previous
     * emission from the {@code sampler} Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/sample.o.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses the emissions of the {@code sampler}
     *      Flowable to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code sample} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param sampler
     *            the Flowable to use for sampling the source Flowable
     * @return an Flowable that emits the results of sampling the items emitted by this Flowable whenever
     *         the {@code sampler} Flowable emits an item or completes
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     */
    public final <U> Flowable<T> sample(Flowable<U> sampler) {
        return lift(new OperatorSampleWithFlowable<T, U>(sampler));
    }

    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable, then feeds the result of that function along with the second item emitted by the source
     * Flowable into the same function, and so on until all items have been emitted by the source Flowable,
     * emitting the result of each of these iterations.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/scan.png" alt="">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code scan} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, whose
     *            result will be emitted to {@link Subscriber}s via {@link Subscriber#onNext onNext} and used in the
     *            next accumulator call
     * @return an Flowable that emits the results of each call to the accumulator function
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX operators documentation: Scan</a>
     */
    public final Flowable<T> scan(Func2<T, T, T> accumulator) {
        return lift(new OperatorScan<T, T>(accumulator));
    }

    /**
     * Returns an Flowable that applies a specified accumulator function to the first item emitted by a source
     * Flowable and a seed value, then feeds the result of that function along with the second item emitted by
     * the source Flowable into the same function, and so on until all items have been emitted by the source
     * Flowable, emitting the result of each of these iterations.
     * <p>
     * <img width="640" height="320" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/scanSeed.png" alt="">
     * <p>
     * This sort of function is sometimes called an accumulator.
     * <p>
     * Note that the Flowable that results from this method will emit {@code initialValue} as its first
     * emitted item.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code scan} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param initialValue
     *            the initial (seed) accumulator item
     * @param accumulator
     *            an accumulator function to be invoked on each item emitted by the source Flowable, whose
     *            result will be emitted to {@link Subscriber}s via {@link Subscriber#onNext onNext} and used in the
     *            next accumulator call
     * @return an Flowable that emits {@code initialValue} followed by the results of each call to the
     *         accumulator function
     * @see <a href="http://reactivex.io/documentation/operators/scan.html">ReactiveX operators documentation: Scan</a>
     */
    public final <R> Flowable<R> scan(R initialValue, Func2<R, ? super T, R> accumulator) {
        return lift(new OperatorScan<R, T>(initialValue, accumulator));
    }

    /**
     * Forces an Flowable's emissions and notifications to be serialized and for it to obey the Rx contract
     * in other ways.
     * <p>
     * It is possible for an Flowable to invoke its Subscribers' methods asynchronously, perhaps from
     * different threads. This could make such an Flowable poorly-behaved, in that it might try to invoke
     * {@code onCompleted} or {@code onError} before one of its {@code onNext} invocations, or it might call
     * {@code onNext} from two different threads concurrently. You can force such an Flowable to be
     * well-behaved and sequential by applying the {@code serialize} method to it.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/synchronize.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code serialize} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @return an {@link Flowable} that is guaranteed to be well-behaved and to make only serialized calls to
     *         its observers
     * @see <a href="http://reactivex.io/documentation/operators/serialize.html">ReactiveX operators documentation: Serialize</a>
     */
    public final Flowable<T> serialize() {
        return lift(OperatorSerialize.<T>instance());
    }

    /**
     * Returns a new {@link Flowable} that multicasts (shares) the original {@link Flowable}. As long as
     * there is at least one {@link Subscriber} this {@link Flowable} will be subscribed and emitting data. 
     * When all subscribers have unsubscribed it will unsubscribe from the source {@link Flowable}. 
     * <p>
     * This is an alias for {@link #publish()}.{@link ConnectableFlowable#refCount()}.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishRefCount.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure because multicasting means the stream is "hot" with
     *      multiple subscribers. Each child will need to manage backpressure independently using operators such
     *      as {@link #onBackpressureDrop} and {@link #onBackpressureBuffer}.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code share} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an {@code Flowable} that upon connection causes the source {@code Flowable} to emit items
     *         to its {@link Subscriber}s
     * @see <a href="http://reactivex.io/documentation/operators/refcount.html">ReactiveX operators documentation: RefCount</a>
     */
    public final Flowable<T> share() {
        return publish().refCount();
    }

    /**
     * Returns an Flowable that skips the first {@code num} items emitted by the source Flowable and emits
     * the remainder.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code skip} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param num
     *            the number of items to skip
     * @return an Flowable that is identical to the source Flowable except that it does not emit the first
     *         {@code num} items that the source Flowable emits
     * @see <a href="http://reactivex.io/documentation/operators/skip.html">ReactiveX operators documentation: Skip</a>
     */
    public final Flowable<T> skip(int num) {
        return lift(new OperatorSkip<T>(num));
    }

    /**
     * Returns an Flowable that skips values emitted by the source Flowable before a specified time window
     * elapses.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skip.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code skip} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window to skip
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that skips values emitted by the source Flowable before the time window defined
     *         by {@code time} elapses and the emits the remainder
     * @see <a href="http://reactivex.io/documentation/operators/skip.html">ReactiveX operators documentation: Skip</a>
     */
    public final Flowable<T> skip(long time, TimeUnit unit) {
        return skip(time, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that skips values emitted by the source Flowable before a specified time window
     * on a specified {@link Scheduler} elapses.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skip.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window to skip
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the {@link Scheduler} on which the timed wait happens
     * @return an Flowable that skips values emitted by the source Flowable before the time window defined
     *         by {@code time} and {@code scheduler} elapses, and then emits the remainder
     * @see <a href="http://reactivex.io/documentation/operators/skip.html">ReactiveX operators documentation: Skip</a>
     */
    public final Flowable<T> skip(long time, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorSkipTimed<T>(time, unit, scheduler));
    }

    /**
     * Returns an Flowable that drops a specified number of items from the end of the sequence emitted by the
     * source Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.png" alt="">
     * <p>
     * This Subscriber accumulates a queue long enough to store the first {@code count} items. As more items are
     * received, items are taken from the front of the queue and emitted by the returned Flowable. This causes
     * such items to be delayed.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code skipLast} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            number of items to drop from the end of the source sequence
     * @return an Flowable that emits the items emitted by the source Flowable except for the dropped ones
     *         at the end
     * @throws IndexOutOfBoundsException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    public final Flowable<T> skipLast(int count) {
        return lift(new OperatorSkipLast<T>(count));
    }

    /**
     * Returns an Flowable that drops items emitted by the source Flowable during a specified time window
     * before the source completes.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.t.png" alt="">
     * <p>
     * Note: this action will cache the latest items arriving in the specified time window.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code skipLast} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that drops those items emitted by the source Flowable in a time window before the
     *         source completes defined by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    public final Flowable<T> skipLast(long time, TimeUnit unit) {
        return skipLast(time, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that drops items emitted by the source Flowable during a specified time window
     * (defined on a specified scheduler) before the source completes.
     * <p>
     * <img width="640" height="340" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipLast.ts.png" alt="">
     * <p>
     * Note: this action will cache the latest items arriving in the specified time window.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     *
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the scheduler used as the time source
     * @return an Flowable that drops those items emitted by the source Flowable in a time window before the
     *         source completes defined by {@code time} and {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/skiplast.html">ReactiveX operators documentation: SkipLast</a>
     */
    public final Flowable<T> skipLast(long time, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorSkipLastTimed<T>(time, unit, scheduler));
    }

    /**
     * Returns an Flowable that skips items emitted by the source Flowable until a second Flowable emits
     * an item.
     * <p>
     * <img width="640" height="375" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipUntil.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code skipUntil} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param other
     *            the second Flowable that has to emit an item before the source Flowable's elements begin
     *            to be mirrored by the resulting Flowable
     * @return an Flowable that skips items from the source Flowable until the second Flowable emits an
     *         item, then emits the remaining items
     * @see <a href="http://reactivex.io/documentation/operators/skipuntil.html">ReactiveX operators documentation: SkipUntil</a>
     */
    public final <U> Flowable<T> skipUntil(Flowable<U> other) {
        return lift(new OperatorSkipUntil<T, U>(other));
    }

    /**
     * Returns an Flowable that skips all items emitted by the source Flowable as long as a specified
     * condition holds true, but emits all further source items as soon as the condition becomes false.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/skipWhile.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code skipWhile} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function to test each item emitted from the source Flowable
     * @return an Flowable that begins emitting items emitted by the source Flowable when the specified
     *         predicate becomes false
     * @see <a href="http://reactivex.io/documentation/operators/skipwhile.html">ReactiveX operators documentation: SkipWhile</a>
     */
    public final Flowable<T> skipWhile(Predicate<? super T> predicate) {
        return lift(new OperatorSkipWhile<T>(OperatorSkipWhile.toPredicate2(predicate)));
    }

    /**
     * Returns an Flowable that emits the items in a specified {@link Flowable} before it begins to emit
     * items emitted by the source Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.o.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param values
     *            an Flowable that contains the items you want the modified Flowable to emit first
     * @return an Flowable that emits the items in the specified {@link Flowable} and then emits the items
     *         emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(Flowable<T> values) {
        return concat(values, this);
    }

    /**
     * Returns an Flowable that emits the items in a specified {@link Iterable} before it begins to emit items
     * emitted by the source Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param values
     *            an Iterable that contains the items you want the modified Flowable to emit first
     * @return an Flowable that emits the items in the specified {@link Iterable} and then emits the items
     *         emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(Iterable<T> values) {
        return concat(Flowable.<T> from(values), this);
    }

    /**
     * Returns an Flowable that emits a specified item before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the item to emit
     * @return an Flowable that emits the specified item before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1) {
        return concat(just(t1), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2) {
        return concat(just(t1, t2), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @param t3
     *            the third item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2, T t3) {
        return concat(just(t1, t2, t3), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @param t3
     *            the third item to emit
     * @param t4
     *            the fourth item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2, T t3, T t4) {
        return concat(just(t1, t2, t3, t4), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @param t3
     *            the third item to emit
     * @param t4
     *            the fourth item to emit
     * @param t5
     *            the fifth item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2, T t3, T t4, T t5) {
        return concat(just(t1, t2, t3, t4, t5), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @param t3
     *            the third item to emit
     * @param t4
     *            the fourth item to emit
     * @param t5
     *            the fifth item to emit
     * @param t6
     *            the sixth item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted
     *         by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2, T t3, T t4, T t5, T t6) {
        return concat(just(t1, t2, t3, t4, t5, t6), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @param t3
     *            the third item to emit
     * @param t4
     *            the fourth item to emit
     * @param t5
     *            the fifth item to emit
     * @param t6
     *            the sixth item to emit
     * @param t7
     *            the seventh item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2, T t3, T t4, T t5, T t6, T t7) {
        return concat(just(t1, t2, t3, t4, t5, t6, t7), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @param t3
     *            the third item to emit
     * @param t4
     *            the fourth item to emit
     * @param t5
     *            the fifth item to emit
     * @param t6
     *            the sixth item to emit
     * @param t7
     *            the seventh item to emit
     * @param t8
     *            the eighth item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2, T t3, T t4, T t5, T t6, T t7, T t8) {
        return concat(just(t1, t2, t3, t4, t5, t6, t7, t8), this);
    }

    /**
     * Returns an Flowable that emits the specified items before it begins to emit items emitted by the source
     * Flowable.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/startWith.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code startWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param t1
     *            the first item to emit
     * @param t2
     *            the second item to emit
     * @param t3
     *            the third item to emit
     * @param t4
     *            the fourth item to emit
     * @param t5
     *            the fifth item to emit
     * @param t6
     *            the sixth item to emit
     * @param t7
     *            the seventh item to emit
     * @param t8
     *            the eighth item to emit
     * @param t9
     *            the ninth item to emit
     * @return an Flowable that emits the specified items before it begins to emit items emitted by the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/startwith.html">ReactiveX operators documentation: StartWith</a>
     */
    public final Flowable<T> startWith(T t1, T t2, T t3, T t4, T t5, T t6, T t7, T t8, T t9) {
        return concat(just(t1, t2, t3, t4, t5, t6, t7, t8, t9), this);
    }

    /**
     * Returns a new Flowable by applying a function that you supply to each item emitted by the source
     * Flowable that returns an Flowable, and then emitting the items emitted by the most recently emitted
     * of these Flowables.
     * <p>
     * <img width="640" height="350" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/switchMap.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code switchMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param func
     *            a function that, when applied to an item emitted by the source Flowable, returns an
     *            Flowable
     * @return an Flowable that emits the items emitted by the Flowable returned from applying {@code func} to the most recently emitted item emitted by the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/flatmap.html">ReactiveX operators documentation: FlatMap</a>
     */
    public final <R> Flowable<R> switchMap(Function<? super T, ? extends Flowable<? extends R>> func) {
        return switchOnNext(map(func));
    }

    /**
     * Returns an Flowable that emits only the first {@code num} items emitted by the source Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.png" alt="">
     * <p>
     * This method returns an Flowable that will invoke a subscribing {@link Subscriber}'s
     * {@link Subscriber#onNext onNext} function a maximum of {@code num} times before invoking
     * {@link Subscriber#onCompleted onCompleted}.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code take} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param num
     *            the maximum number of items to emit
     * @return an Flowable that emits only the first {@code num} items emitted by the source Flowable, or
     *         all of the items from the source Flowable if that Flowable emits fewer than {@code num} items
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    public final Flowable<T> take(final int num) {
        return lift(new OperatorTake<T>(num));
    }

    /**
     * Returns an Flowable that emits those items emitted by source Flowable before a specified time runs
     * out.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code take} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that emits those items emitted by the source Flowable before the time runs out
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    public final Flowable<T> take(long time, TimeUnit unit) {
        return take(time, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits those items emitted by source Flowable before a specified time (on a
     * specified Scheduler) runs out.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/take.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler used for time source
     * @return an Flowable that emits those items emitted by the source Flowable before the time runs out,
     *         according to the specified Scheduler
     * @see <a href="http://reactivex.io/documentation/operators/take.html">ReactiveX operators documentation: Take</a>
     */
    public final Flowable<T> take(long time, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorTakeTimed<T>(time, unit, scheduler));
    }

    /**
     * Returns an Flowable that emits only the very first item emitted by the source Flowable that satisfies
     * a specified condition.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeFirstN.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeFirst} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            the condition any item emitted by the source Flowable has to satisfy
     * @return an Flowable that emits only the very first item emitted by the source Flowable that satisfies
     *         the given condition, or that completes without emitting anything if the source Flowable
     *         completes without emitting a single condition-satisfying item
     * @see <a href="http://reactivex.io/documentation/operators/first.html">ReactiveX operators documentation: First</a>
     */
    public final Flowable<T> takeFirst(Predicate<? super T> predicate) {
        return filter(predicate).take(1);
    }

    /**
     * Returns an Flowable that emits at most a specified number of items from the source Flowable that were
     * emitted in a specified window of time before the Flowable completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.tn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLast} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that emits at most {@code count} items from the source Flowable that were emitted
     *         in a specified window of time before the Flowable completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<T> takeLast(int count, long time, TimeUnit unit) {
        return takeLast(count, time, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits at most a specified number of items from the source Flowable that were
     * emitted in a specified window of time before the Flowable completed, where the timing information is
     * provided by a given Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.tns.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the {@link Scheduler} that provides the timestamps for the observed items
     * @return an Flowable that emits at most {@code count} items from the source Flowable that were emitted
     *         in a specified window of time before the Flowable completed, where the timing information is
     *         provided by the given {@code scheduler}
     * @throws IndexOutOfBoundsException
     *             if {@code count} is less than zero
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<T> takeLast(int count, long time, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorTakeLastTimed<T>(count, time, unit, scheduler));
    }

    /**
     * Returns an Flowable that emits the items from the source Flowable that were emitted in a specified
     * window of time before the Flowable completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLast} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that emits the items from the source Flowable that were emitted in the window of
     *         time before the Flowable completed specified by {@code time}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<T> takeLast(long time, TimeUnit unit) {
        return takeLast(time, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits the items from the source Flowable that were emitted in a specified
     * window of time before the Flowable completed, where the timing information is provided by a specified
     * Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLast.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the Observed items
     * @return an Flowable that emits the items from the source Flowable that were emitted in the window of
     *         time before the Flowable completed specified by {@code time}, where the timing information is
     *         provided by {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<T> takeLast(long time, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorTakeLastTimed<T>(time, unit, scheduler));
    }

    /**
     * Returns an Flowable that emits a single List containing the last {@code count} elements emitted by the
     * source Flowable.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLastBuffer} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the number of items to emit in the list
     * @return an Flowable that emits a single list containing the last {@code count} elements emitted by the
     *         source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<List<T>> takeLastBuffer(int count) {
        return takeLast(count).toList();
    }

    /**
     * Returns an Flowable that emits a single List containing at most {@code count} items from the source
     * Flowable that were emitted during a specified window of time before the source Flowable completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.tn.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLastBuffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that emits a single List containing at most {@code count} items emitted by the
     *         source Flowable during the time window defined by {@code time} before the source Flowable
     *         completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<List<T>> takeLastBuffer(int count, long time, TimeUnit unit) {
        return takeLast(count, time, unit).toList();
    }

    /**
     * Returns an Flowable that emits a single List containing at most {@code count} items from the source
     * Flowable that were emitted during a specified window of time (on a specified Scheduler) before the
     * source Flowable completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.tns.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param count
     *            the maximum number of items to emit
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the observed items
     * @return an Flowable that emits a single List containing at most {@code count} items emitted by the
     *         source Flowable during the time window defined by {@code time} before the source Flowable
     *         completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<List<T>> takeLastBuffer(int count, long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(count, time, unit, scheduler).toList();
    }

    /**
     * Returns an Flowable that emits a single List containing those items from the source Flowable that
     * were emitted during a specified window of time before the source Flowable completed.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.t.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code takeLastBuffer} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @return an Flowable that emits a single List containing the items emitted by the source Flowable
     *         during the time window defined by {@code time} before the source Flowable completed
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<List<T>> takeLastBuffer(long time, TimeUnit unit) {
        return takeLast(time, unit).toList();
    }

    /**
     * Returns an Flowable that emits a single List containing those items from the source Flowable that
     * were emitted during a specified window of time before the source Flowable completed, where the timing
     * information is provided by the given Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeLastBuffer.ts.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param time
     *            the length of the time window
     * @param unit
     *            the time unit of {@code time}
     * @param scheduler
     *            the Scheduler that provides the timestamps for the observed items
     * @return an Flowable that emits a single List containing the items emitted by the source Flowable
     *         during the time window defined by {@code time} before the source Flowable completed, where the
     *         timing information is provided by {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/takelast.html">ReactiveX operators documentation: TakeLast</a>
     */
    public final Flowable<List<T>> takeLastBuffer(long time, TimeUnit unit, Scheduler scheduler) {
        return takeLast(time, unit, scheduler).toList();
    }

    /**
     * Returns an Flowable that emits the items emitted by the source Flowable until a second Flowable
     * emits an item.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeUntil.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeUntil} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param other
     *            the Flowable whose first emitted item will cause {@code takeUntil} to stop emitting items
     *            from the source Flowable
     * @param <E>
     *            the type of items emitted by {@code other}
     * @return an Flowable that emits the items emitted by the source Flowable until such time as {@code other} emits its first item
     * @see <a href="http://reactivex.io/documentation/operators/takeuntil.html">ReactiveX operators documentation: TakeUntil</a>
     */
    public final <E> Flowable<T> takeUntil(Flowable<? extends E> other) {
        return lift(new OperatorTakeUntil<T, E>(other));
    }

    /**
     * Returns an Flowable that emits items emitted by the source Flowable so long as each item satisfied a
     * specified condition, and then completes as soon as this condition is not satisfied.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeWhile.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code takeWhile} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param predicate
     *            a function that evaluates an item emitted by the source Flowable and returns a Boolean
     * @return an Flowable that emits the items from the source Flowable so long as each item satisfies the
     *         condition defined by {@code predicate}, then completes
     * @see <a href="http://reactivex.io/documentation/operators/takewhile.html">ReactiveX operators documentation: TakeWhile</a>
     * @see Flowable#takeUntil(Function)
     */
    public final Flowable<T> takeWhile(final Predicate<? super T> predicate) {
        return lift(new OperatorTakeWhile<T>(predicate));
    }

    /**
     * Returns an Flowable that emits items emitted by the source Flowable, checks the specified predicate
     * for each item, and then completes if the condition is satisfied.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/takeUntil.p.png" alt="">
     * <p>
     * The difference between this operator and {@link #takeWhile(Function)} is that here, the condition is
     * evaluated <em>after</em> the item is emitted.
     * 
     * @warn "Scheduler" and "Backpressure Support" sections missing from javadocs
     * @param stopPredicate 
     *            a function that evaluates an item emitted by the source Flowable and returns a Boolean
     * @return an Flowable that first emits items emitted by the source Flowable, checks the specified
     *         condition after each item, and then completes if the condition is satisfied.
     * @see <a href="http://reactivex.io/documentation/operators/takeuntil.html">ReactiveX operators documentation: TakeUntil</a>
     * @see Flowable#takeWhile(Function)
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     */
    @Experimental
    public final Flowable<T> takeUntil(final Predicate<? super T> stopPredicate) {
        return lift(new OperatorTakeUntilPredicate<T>(stopPredicate));
    }
    
    /**
     * Returns an Flowable that emits only the first item emitted by the source Flowable during sequential
     * time windows of a specified duration.
     * <p>
     * This differs from {@link #throttleLast} in that this only tracks passage of time whereas
     * {@link #throttleLast} ticks at scheduled intervals.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleFirst.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code throttleFirst} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param windowDuration
     *            time to wait before emitting another item after emitting the last item
     * @param unit
     *            the unit of time of {@code windowDuration}
     * @return an Flowable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     */
    public final Flowable<T> throttleFirst(long windowDuration, TimeUnit unit) {
        return throttleFirst(windowDuration, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits only the first item emitted by the source Flowable during sequential
     * time windows of a specified duration, where the windows are managed by a specified Scheduler.
     * <p>
     * This differs from {@link #throttleLast} in that this only tracks passage of time whereas
     * {@link #throttleLast} ticks at scheduled intervals.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleFirst.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param skipDuration
     *            time to wait before emitting another item after emitting the last item
     * @param unit
     *            the unit of time of {@code skipDuration}
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle timeout for each
     *            event
     * @return an Flowable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     */
    public final Flowable<T> throttleFirst(long skipDuration, TimeUnit unit, Scheduler scheduler) {
        return lift(new OperatorThrottleFirst<T>(skipDuration, unit, scheduler));
    }

    /**
     * Returns an Flowable that emits only the last item emitted by the source Flowable during sequential
     * time windows of a specified duration.
     * <p>
     * This differs from {@link #throttleFirst} in that this ticks along at a scheduled interval whereas
     * {@link #throttleFirst} does not tick, it just tracks passage of time.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleLast.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code throttleLast} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param intervalDuration
     *            duration of windows within which the last item emitted by the source Flowable will be
     *            emitted
     * @param unit
     *            the unit of time of {@code intervalDuration}
     * @return an Flowable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #sample(long, TimeUnit)
     */
    public final Flowable<T> throttleLast(long intervalDuration, TimeUnit unit) {
        return sample(intervalDuration, unit);
    }

    /**
     * Returns an Flowable that emits only the last item emitted by the source Flowable during sequential
     * time windows of a specified duration, where the duration is governed by a specified Scheduler.
     * <p>
     * This differs from {@link #throttleFirst} in that this ticks along at a scheduled interval whereas
     * {@link #throttleFirst} does not tick, it just tracks passage of time.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleLast.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param intervalDuration
     *            duration of windows within which the last item emitted by the source Flowable will be
     *            emitted
     * @param unit
     *            the unit of time of {@code intervalDuration}
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle timeout for each
     *            event
     * @return an Flowable that performs the throttle operation
     * @see <a href="http://reactivex.io/documentation/operators/sample.html">ReactiveX operators documentation: Sample</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #sample(long, TimeUnit, Scheduler)
     */
    public final Flowable<T> throttleLast(long intervalDuration, TimeUnit unit, Scheduler scheduler) {
        return sample(intervalDuration, unit, scheduler);
    }

    /**
     * Returns an Flowable that only emits those items emitted by the source Flowable that are not followed
     * by another emitted item within a specified time window.
     * <p>
     * <em>Note:</em> If the source Flowable keeps emitting items more frequently than the length of the time
     * window then no items will be emitted by the resulting Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleWithTimeout.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code throttleWithTimeout} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            the length of the window of time that must pass after the emission of an item from the source
     *            Flowable in which that Flowable emits no items in order for the item to be emitted by the
     *            resulting Flowable
     * @param unit
     *            the {@link TimeUnit} of {@code timeout}
     * @return an Flowable that filters out items that are too quickly followed by newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #debounce(long, TimeUnit)
     */
    public final Flowable<T> throttleWithTimeout(long timeout, TimeUnit unit) {
        return debounce(timeout, unit);
    }

    /**
     * Returns an Flowable that only emits those items emitted by the source Flowable that are not followed
     * by another emitted item within a specified time window, where the time window is governed by a specified
     * Scheduler.
     * <p>
     * <em>Note:</em> If the source Flowable keeps emitting items more frequently than the length of the time
     * window then no items will be emitted by the resulting Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/throttleWithTimeout.s.png" alt="">
     * <p>
     * Information on debounce vs throttle:
     * <p>
     * <ul>
     * <li><a href="http://drupalmotion.com/article/debounce-and-throttle-visual-explanation">Debounce and Throttle: visual explanation</a></li>
     * <li><a href="http://unscriptable.com/2009/03/20/debouncing-javascript-methods/">Debouncing: javascript methods</a></li>
     * <li><a href="http://www.illyriad.co.uk/blog/index.php/2011/09/javascript-dont-spam-your-server-debounce-and-throttle/">Javascript - don't spam your server: debounce and throttle</a></li>
     * </ul>
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            the length of the window of time that must pass after the emission of an item from the source
     *            Flowable in which that Flowable emits no items in order for the item to be emitted by the
     *            resulting Flowable
     * @param unit
     *            the {@link TimeUnit} of {@code timeout}
     * @param scheduler
     *            the {@link Scheduler} to use internally to manage the timers that handle the timeout for each
     *            item
     * @return an Flowable that filters out items that are too quickly followed by newer items
     * @see <a href="http://reactivex.io/documentation/operators/debounce.html">ReactiveX operators documentation: Debounce</a>
     * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Backpressure">RxJava wiki: Backpressure</a>
     * @see #debounce(long, TimeUnit, Scheduler)
     */
    public final Flowable<T> throttleWithTimeout(long timeout, TimeUnit unit, Scheduler scheduler) {
        return debounce(timeout, unit, scheduler);
    }

    /**
     * Returns an Flowable that emits records of the time interval between consecutive items emitted by the
     * source Flowable.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeInterval.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timeInterval} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits time interval information items
     * @see <a href="http://reactivex.io/documentation/operators/timeinterval.html">ReactiveX operators documentation: TimeInterval</a>
     */
    public final Flowable<TimeInterval<T>> timeInterval() {
        return timeInterval(Schedulers.immediate());
    }

    /**
     * Returns an Flowable that emits records of the time interval between consecutive items emitted by the
     * source Flowable, where this interval is computed on a specified Scheduler.
     * <p>
     * <img width="640" height="315" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeInterval.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} used to compute time intervals
     * @return an Flowable that emits time interval information items
     * @see <a href="http://reactivex.io/documentation/operators/timeinterval.html">ReactiveX operators documentation: TimeInterval</a>
     */
    public final Flowable<TimeInterval<T>> timeInterval(Scheduler scheduler) {
        return lift(new OperatorTimeInterval<T>(scheduler));
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, but notifies observers of a
     * {@code TimeoutException} if either the first item emitted by the source Flowable or any subsequent item
     * doesn't arrive within time windows defined by other Flowables.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout5.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the first timeout value type (ignored)
     * @param <V>
     *            the subsequent timeout value type (ignored)
     * @param firstTimeoutSelector
     *            a function that returns an Flowable that determines the timeout window for the first source
     *            item
     * @param timeoutSelector
     *            a function that returns an Flowable for each item emitted by the source Flowable and that
     *            determines the timeout window in which the subsequent source item must arrive in order to
     *            continue the sequence
     * @return an Flowable that mirrors the source Flowable, but notifies observers of a
     *         {@code TimeoutException} if either the first item or any subsequent item doesn't arrive within
     *         the time windows specified by the timeout selectors
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final <U, V> Flowable<T> timeout(Supplier<? extends Flowable<U>> firstTimeoutSelector, Function<? super T, ? extends Flowable<V>> timeoutSelector) {
        return timeout(firstTimeoutSelector, timeoutSelector, null);
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, but switches to a fallback Flowable if either
     * the first item emitted by the source Flowable or any subsequent item doesn't arrive within time windows
     * defined by other Flowables.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout6.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the first timeout value type (ignored)
     * @param <V>
     *            the subsequent timeout value type (ignored)
     * @param firstTimeoutSelector
     *            a function that returns an Flowable which determines the timeout window for the first source
     *            item
     * @param timeoutSelector
     *            a function that returns an Flowable for each item emitted by the source Flowable and that
     *            determines the timeout window in which the subsequent source item must arrive in order to
     *            continue the sequence
     * @param other
     *            the fallback Flowable to switch to if the source Flowable times out
     * @return an Flowable that mirrors the source Flowable, but switches to the {@code other} Flowable if
     *         either the first item emitted by the source Flowable or any subsequent item doesn't arrive
     *         within time windows defined by the timeout selectors
     * @throws NullPointerException
     *             if {@code timeoutSelector} is null
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final <U, V> Flowable<T> timeout(Supplier<? extends Flowable<U>> firstTimeoutSelector, Function<? super T, ? extends Flowable<V>> timeoutSelector, Flowable<? extends T> other) {
        if (timeoutSelector == null) {
            throw new NullPointerException("timeoutSelector is null");
        }
        return lift(new OperatorTimeoutWithSelector<T, U, V>(firstTimeoutSelector, timeoutSelector, other));
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, but notifies observers of a
     * {@code TimeoutException} if an item emitted by the source Flowable doesn't arrive within a window of
     * time after the emission of the previous item, where that period of time is measured by an Flowable that
     * is a function of the previous item.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout3.png" alt="">
     * <p>
     * Note: The arrival of the first source item is never timed out.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <V>
     *            the timeout value type (ignored)
     * @param timeoutSelector
     *            a function that returns an observable for each item emitted by the source
     *            Flowable and that determines the timeout window for the subsequent item
     * @return an Flowable that mirrors the source Flowable, but notifies observers of a
     *         {@code TimeoutException} if an item emitted by the source Flowable takes longer to arrive than
     *         the time window defined by the selector for the previously emitted item
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final <V> Flowable<T> timeout(Function<? super T, ? extends Flowable<V>> timeoutSelector) {
        return timeout(null, timeoutSelector, null);
    }

    /**
     * Returns an Flowable that mirrors the source Flowable, but that switches to a fallback Flowable if
     * an item emitted by the source Flowable doesn't arrive within a window of time after the emission of the
     * previous item, where that period of time is measured by an Flowable that is a function of the previous
     * item.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout4.png" alt="">
     * <p>
     * Note: The arrival of the first source item is never timed out.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <V>
     *            the timeout value type (ignored)
     * @param timeoutSelector
     *            a function that returns an Flowable, for each item emitted by the source Flowable, that
     *            determines the timeout window for the subsequent item
     * @param other
     *            the fallback Flowable to switch to if the source Flowable times out
     * @return an Flowable that mirrors the source Flowable, but switches to mirroring a fallback Flowable
     *         if an item emitted by the source Flowable takes longer to arrive than the time window defined
     *         by the selector for the previously emitted item
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final <V> Flowable<T> timeout(Function<? super T, ? extends Flowable<V>> timeoutSelector, Flowable<? extends T> other) {
        return timeout(null, timeoutSelector, other);
    }

    /**
     * Returns an Flowable that mirrors the source Flowable but applies a timeout policy for each emitted
     * item. If the next item isn't emitted within the specified timeout duration starting from its predecessor,
     * the resulting Flowable begins instead to mirror a fallback Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.2.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code timeout} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument
     * @param other
     *            the fallback Flowable to use in case of a timeout
     * @return the source Flowable modified to switch to the fallback Flowable in case of a timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final Flowable<T> timeout(long timeout, TimeUnit timeUnit, Flowable<? extends T> other) {
        return timeout(timeout, timeUnit, other, Schedulers.computation());
    }

    /**
     * Returns an Flowable that mirrors the source Flowable but applies a timeout policy for each emitted
     * item using a specified Scheduler. If the next item isn't emitted within the specified timeout duration
     * starting from its predecessor, the resulting Flowable begins instead to mirror a fallback Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timeout.2s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timeout
     *            maximum duration between items before a timeout occurs
     * @param timeUnit
     *            the unit of time that applies to the {@code timeout} argument
     * @param other
     *            the Flowable to use as the fallback in case of a timeout
     * @param scheduler
     *            the {@link Scheduler} to run the timeout timers on
     * @return the source Flowable modified so that it will switch to the fallback Flowable in case of a
     *         timeout
     * @see <a href="http://reactivex.io/documentation/operators/timeout.html">ReactiveX operators documentation: Timeout</a>
     */
    public final Flowable<T> timeout(long timeout, TimeUnit timeUnit, Flowable<? extends T> other, Scheduler scheduler) {
        return lift(new OperatorTimeout<T>(timeout, timeUnit, other, scheduler));
    }

    /**
     * Returns an Flowable that emits each item emitted by the source Flowable, wrapped in a
     * {@link Timestamped} object.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timestamp.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code timestamp} operates by default on the {@code immediate} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return an Flowable that emits timestamped items from the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/timestamp.html">ReactiveX operators documentation: Timestamp</a>
     */
    public final Flowable<Timestamped<T>> timestamp() {
        return timestamp(Schedulers.immediate());
    }

    /**
     * Returns an Flowable that emits each item emitted by the source Flowable, wrapped in a
     * {@link Timestamped} object whose timestamps are provided by a specified Scheduler.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/timestamp.s.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param scheduler
     *            the {@link Scheduler} to use as a time source
     * @return an Flowable that emits timestamped items from the source Flowable with timestamps provided by
     *         the {@code scheduler}
     * @see <a href="http://reactivex.io/documentation/operators/timestamp.html">ReactiveX operators documentation: Timestamp</a>
     */
    public final Flowable<Timestamped<T>> timestamp(Scheduler scheduler) {
        return lift(new OperatorTimestamp<T>(scheduler));
    }

    /**
     * Returns an Flowable that emits a single HashMap containing all items emitted by the source Flowable,
     * mapped by the keys returned by a specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMap.png" alt="">
     * <p>
     * If more than one source item maps to the same key, the HashMap will contain the latest of those items.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            the function that extracts the key from a source item to be used in the HashMap
     * @return an Flowable that emits a single item: a HashMap containing the mapped items from the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final <K> Flowable<Map<K, T>> toMap(Function<? super T, ? extends K> keySelector) {
        return lift(new OperatorToMap<T, K, T>(keySelector, UtilityFunctions.<T>identity()));
    }

    /**
     * Returns an Flowable that emits a single HashMap containing values corresponding to items emitted by the
     * source Flowable, mapped by the keys returned by a specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMap.png" alt="">
     * <p>
     * If more than one source item maps to the same key, the HashMap will contain a single entry that
     * corresponds to the latest of those items.
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            the function that extracts the key from a source item to be used in the HashMap
     * @param valueSelector
     *            the function that extracts the value from a source item to be used in the HashMap
     * @return an Flowable that emits a single item: a HashMap containing the mapped items from the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final <K, V> Flowable<Map<K, V>> toMap(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector) {
        return lift(new OperatorToMap<T, K, V>(keySelector, valueSelector));
    }

    /**
     * Returns an Flowable that emits a single Map, returned by a specified {@code mapFactory} function, that
     * contains keys and values extracted from the items emitted by the source Flowable.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMap.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            the function that extracts the key from a source item to be used in the Map
     * @param valueSelector
     *            the function that extracts the value from the source items to be used as value in the Map
     * @param mapFactory
     *            the function that returns a Map instance to be used
     * @return an Flowable that emits a single item: a Map that contains the mapped items emitted by the
     *         source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final <K, V> Flowable<Map<K, V>> toMap(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector, Supplier<? extends Map<K, V>> mapFactory) {
        return lift(new OperatorToMap<T, K, V>(keySelector, valueSelector, mapFactory));
    }

    /**
     * Returns an Flowable that emits a single HashMap that contains an ArrayList of items emitted by the
     * source Flowable keyed by a specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            the function that extracts the key from the source items to be used as key in the HashMap
     * @return an Flowable that emits a single item: a HashMap that contains an ArrayList of items mapped from
     *         the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final <K> Flowable<Map<K, Collection<T>>> toMultimap(Function<? super T, ? extends K> keySelector) {
        return lift(new OperatorToMultimap<T, K, T>(keySelector, UtilityFunctions.<T>identity()));
    }

    /**
     * Returns an Flowable that emits a single HashMap that contains an ArrayList of values extracted by a
     * specified {@code valueSelector} function from items emitted by the source Flowable, keyed by a
     * specified {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            the function that extracts a key from the source items to be used as key in the HashMap
     * @param valueSelector
     *            the function that extracts a value from the source items to be used as value in the HashMap
     * @return an Flowable that emits a single item: a HashMap that contains an ArrayList of items mapped from
     *         the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final <K, V> Flowable<Map<K, Collection<V>>> toMultimap(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector) {
        return lift(new OperatorToMultimap<T, K, V>(keySelector, valueSelector));
    }

    /**
     * Returns an Flowable that emits a single Map, returned by a specified {@code mapFactory} function, that
     * contains an ArrayList of values, extracted by a specified {@code valueSelector} function from items
     * emitted by the source Flowable and keyed by the {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            the function that extracts a key from the source items to be used as the key in the Map
     * @param valueSelector
     *            the function that extracts a value from the source items to be used as the value in the Map
     * @param mapFactory
     *            the function that returns a Map instance to be used
     * @return an Flowable that emits a single item: a Map that contains a list items mapped from the source
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final <K, V> Flowable<Map<K, Collection<V>>> toMultimap(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector, Supplier<? extends Map<K, Collection<V>>> mapFactory) {
        return lift(new OperatorToMultimap<T, K, V>(keySelector, valueSelector, mapFactory));
    }

    /**
     * Returns an Flowable that emits a single Map, returned by a specified {@code mapFactory} function, that
     * contains a custom collection of values, extracted by a specified {@code valueSelector} function from
     * items emitted by the source Flowable, and keyed by the {@code keySelector} function.
     * <p>
     * <img width="640" height="305" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toMultiMap.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toMultiMap} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param keySelector
     *            the function that extracts a key from the source items to be used as the key in the Map
     * @param valueSelector
     *            the function that extracts a value from the source items to be used as the value in the Map
     * @param mapFactory
     *            the function that returns a Map instance to be used
     * @param collectionFactory
     *            the function that returns a Collection instance for a particular key to be used in the Map
     * @return an Flowable that emits a single item: a Map that contains the collection of mapped items from
     *         the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final <K, V> Flowable<Map<K, Collection<V>>> toMultimap(Function<? super T, ? extends K> keySelector, Function<? super T, ? extends V> valueSelector, Supplier<? extends Map<K, Collection<V>>> mapFactory, Function<? super K, ? extends Collection<V>> collectionFactory) {
        return lift(new OperatorToMultimap<T, K, V>(keySelector, valueSelector, mapFactory, collectionFactory));
    }

    /**
     * Returns an Flowable that emits a list that contains the items emitted by the source Flowable, in a
     * sorted order. Each item emitted by the Flowable must implement {@link Comparable} with respect to all
     * other items in the sequence.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toSortedList.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toSortedList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @throws ClassCastException
     *             if any item emitted by the Flowable does not implement {@link Comparable} with respect to
     *             all other items emitted by the Flowable
     * @return an Flowable that emits a list that contains the items emitted by the source Flowable in
     *         sorted order
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final Flowable<List<T>> toSortedList() {
        return lift(new OperatorToFlowableSortedList<T>());
    }

    /**
     * Returns an Flowable that emits a list that contains the items emitted by the source Flowable, in a
     * sorted order based on a specified comparison function.
     * <p>
     * <img width="640" height="310" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/toSortedList.f.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as by intent it is requesting and buffering everything.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code toSortedList} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param sortFunction
     *            a function that compares two items emitted by the source Flowable and returns an Integer
     *            that indicates their sort order
     * @return an Flowable that emits a list that contains the items emitted by the source Flowable in
     *         sorted order
     * @see <a href="http://reactivex.io/documentation/operators/to.html">ReactiveX operators documentation: To</a>
     */
    public final Flowable<List<T>> toSortedList(Func2<? super T, ? super T, Integer> sortFunction) {
        return lift(new OperatorToFlowableSortedList<T>(sortFunction));
    }

    /**
     * Merges the specified Flowable into this Flowable sequence by using the {@code resultSelector}
     * function only when the source Flowable (this instance) emits an item.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/withLatestFrom.png" alt="">
     *
     * @warn "Backpressure Support" section missing from javadoc
     * @warn "Scheduler" section missing from javadoc
     * @param other
     *            the other Flowable
     * @param resultSelector
     *            the function to call when this Flowable emits an item and the other Flowable has already
     *            emitted an item, to generate the item to be emitted by the resulting Flowable
     * @return an Flowable that merges the specified Flowable into this Flowable by using the
     *         {@code resultSelector} function only when the source Flowable sequence (this instance) emits an
     *         item
     * @Experimental The behavior of this can change at any time.
     * @since (if this graduates from Experimental/Beta to supported, replace this parenthetical with the release number)
     * @see <a href="http://reactivex.io/documentation/operators/combinelatest.html">ReactiveX operators documentation: CombineLatest</a>
     */
    @Experimental
    public final <U, R> Flowable<R> withLatestFrom(Flowable<? extends U> other, Func2<? super T, ? super U, ? extends R> resultSelector) {
        return lift(new OperatorWithLatestFrom<T, U, R>(other, resultSelector));
    }
    
    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping windows. It emits the current window and opens a new one
     * whenever the Flowable produced by the specified {@code closingSelector} emits an item.
     * <p>
     * <img width="640" height="485" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window1.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses the {@code closingSelector} to control data
     *      flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param closingSelector
     *            a {@link Supplier} that returns an {@code Flowable} that governs the boundary between windows.
     *            When this {@code Flowable} emits an item, {@code window} emits the current window and begins
     *            a new one.
     * @return an Flowable that emits connected, non-overlapping windows of items from the source Flowable
     *         whenever {@code closingSelector} emits an item
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final <TClosing> Flowable<Flowable<T>> window(Supplier<? extends Flowable<? extends TClosing>> closingSelector) {
        return lift(new OperatorWindowWithFlowable<T, TClosing>(closingSelector));
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping windows, each containing {@code count} items. When the source
     * Flowable completes or encounters an error, the resulting Flowable emits the current window and
     * propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="400" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window3.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses {@code count} to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum size of each window before it should be emitted
     * @return an Flowable that emits connected, non-overlapping windows, each containing at most
     *         {@code count} items from the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(int count) {
        return window(count, count);
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits windows every {@code skip} items, each containing no more than {@code count} items. When
     * the source Flowable completes or encounters an error, the resulting Flowable emits the current window
     * and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="365" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window4.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses {@code count} to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param skip
     *            how many items need to be skipped before starting a new window. Note that if {@code skip} and
     *            {@code count} are equal this is the same operation as {@link #window(int)}.
     * @return an Flowable that emits windows every {@code skip} items containing at most {@code count} items
     *         from the source Flowable
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(int count, int skip) {
        return lift(new OperatorWindowWithSize<T>(count, skip));
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable starts a new window periodically, as determined by the {@code timeshift} argument. It emits
     * each window after a fixed timespan, specified by the {@code timespan} argument. When the source
     * Flowable completes or Flowable completes or encounters an error, the resulting Flowable emits the
     * current window and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window7.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted
     * @param timeshift
     *            the period of time after which a new window will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @return an Flowable that emits new windows periodically as a fixed timespan elapses
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(long timespan, long timeshift, TimeUnit unit) {
        return window(timespan, timeshift, unit, Integer.MAX_VALUE, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable starts a new window periodically, as determined by the {@code timeshift} argument. It emits
     * each window after a fixed timespan, specified by the {@code timespan} argument. When the source
     * Flowable completes or Flowable completes or encounters an error, the resulting Flowable emits the
     * current window and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window7.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted
     * @param timeshift
     *            the period of time after which a new window will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @return an Flowable that emits new windows periodically as a fixed timespan elapses
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(long timespan, long timeshift, TimeUnit unit, Scheduler scheduler) {
        return window(timespan, timeshift, unit, Integer.MAX_VALUE, scheduler);
    }
    
    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable starts a new window periodically, as determined by the {@code timeshift} argument or a maximum
     * size as specified by the {@code count} argument (whichever is reached first). It emits
     * each window after a fixed timespan, specified by the {@code timespan} argument. When the source
     * Flowable completes or Flowable completes or encounters an error, the resulting Flowable emits the
     * current window and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="335" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window7.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted
     * @param timeshift
     *            the period of time after which a new window will be created
     * @param unit
     *            the unit of time that applies to the {@code timespan} and {@code timeshift} arguments
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @return an Flowable that emits new windows periodically as a fixed timespan elapses
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(long timespan, long timeshift, TimeUnit unit, int count, Scheduler scheduler) {
        return lift(new OperatorWindowWithTime<T>(timespan, timeshift, unit, count, scheduler));
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping windows, each of a fixed duration specified by the
     * {@code timespan} argument. When the source Flowable completes or encounters an error, the resulting
     * Flowable emits the current window and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="375" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window5.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time that applies to the {@code timespan} argument
     * @return an Flowable that emits connected, non-overlapping windows represending items emitted by the
     *         source Flowable during fixed, consecutive durations
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(long timespan, TimeUnit unit) {
        return window(timespan, timespan, unit, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping windows, each of a fixed duration as specified by the
     * {@code timespan} argument or a maximum size as specified by the {@code count} argument (whichever is
     * reached first). When the source Flowable completes or encounters an error, the resulting Flowable
     * emits the current window and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window6.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} operates by default on the {@code computation} {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time that applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each window before it should be emitted
     * @return an Flowable that emits connected, non-overlapping windows of items from the source Flowable
     *         that were emitted during a fixed duration of time or when the window has reached maximum capacity
     *         (whichever occurs first)
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(long timespan, TimeUnit unit, int count) {
        return window(timespan, unit, count, Schedulers.computation());
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping windows, each of a fixed duration specified by the
     * {@code timespan} argument or a maximum size specified by the {@code count} argument (whichever is reached
     * first). When the source Flowable completes or encounters an error, the resulting Flowable emits the
     * current window and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="370" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window6.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param count
     *            the maximum size of each window before it should be emitted
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @return an Flowable that emits connected, non-overlapping windows of items from the source Flowable
     *         that were emitted during a fixed duration of time or when the window has reached maximum capacity
     *         (whichever occurs first)
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(long timespan, TimeUnit unit, int count, Scheduler scheduler) {
        return window(timespan, timespan, unit, count, scheduler);
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits connected, non-overlapping windows, each of a fixed duration as specified by the
     * {@code timespan} argument. When the source Flowable completes or encounters an error, the resulting
     * Flowable emits the current window and propagates the notification from the source Flowable.
     * <p>
     * <img width="640" height="375" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window5.s.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses time to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>you specify which {@link Scheduler} this operator will use</dd>
     * </dl>
     * 
     * @param timespan
     *            the period of time each window collects items before it should be emitted and replaced with a
     *            new window
     * @param unit
     *            the unit of time which applies to the {@code timespan} argument
     * @param scheduler
     *            the {@link Scheduler} to use when determining the end and start of a window
     * @return an Flowable that emits connected, non-overlapping windows containing items emitted by the
     *         source Flowable within a fixed duration
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final Flowable<Flowable<T>> window(long timespan, TimeUnit unit, Scheduler scheduler) {
        return window(timespan, unit, Integer.MAX_VALUE, scheduler);
    }

    /**
     * Returns an Flowable that emits windows of items it collects from the source Flowable. The resulting
     * Flowable emits windows that contain those items emitted by the source Flowable between the time when
     * the {@code windowOpenings} Flowable emits an item and when the Flowable returned by
     * {@code closingSelector} emits an item.
     * <p>
     * <img width="640" height="550" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window2.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses Flowables to control data flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param windowOpenings
     *            an Flowable that, when it emits an item, causes another window to be created
     * @param closingSelector
     *            a {@link Function} that produces an Flowable for every window created. When this Flowable
     *            emits an item, the associated window is closed and emitted
     * @return an Flowable that emits windows of items emitted by the source Flowable that are governed by
     *         the specified window-governing Flowables
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final <TOpening, TClosing> Flowable<Flowable<T>> window(Flowable<? extends TOpening> windowOpenings, Function<? super TOpening, ? extends Flowable<? extends TClosing>> closingSelector) {
        return lift(new OperatorWindowWithStartEndFlowable<T, TOpening, TClosing>(windowOpenings, closingSelector));
    }

    /**
     * Returns an Flowable that emits non-overlapping windows of items it collects from the source Flowable
     * where the boundary of each window is determined by the items emitted from a specified boundary-governing
     * Flowable.
     * <p>
     * <img width="640" height="475" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/window8.png" alt="">
     * <dl>
     *  <dt><b>Backpressure Support:</b></dt>
     *  <dd>This operator does not support backpressure as it uses a {@code boundary} Flowable to control data
     *      flow.</dd>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>This version of {@code window} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <U>
     *            the window element type (ignored)
     * @param boundary
     *            an Flowable whose emitted items close and open windows
     * @return an Flowable that emits non-overlapping windows of items it collects from the source Flowable
     *         where the boundary of each window is determined by the items emitted from the {@code boundary}
     *         Flowable
     * @see <a href="http://reactivex.io/documentation/operators/window.html">ReactiveX operators documentation: Window</a>
     */
    public final <U> Flowable<Flowable<T>> window(Flowable<U> boundary) {
        return lift(new OperatorWindowWithFlowable<T, U>(boundary));
    }

    /**
     * Returns an Flowable that emits items that are the result of applying a specified function to pairs of
     * values, one each from the source Flowable and a specified Iterable sequence.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.i.png" alt="">
     * <p>
     * Note that the {@code other} Iterable is evaluated as items are observed from the source Flowable; it is
     * not pre-consumed. This allows you to zip infinite streams on either side.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zipWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T2>
     *            the type of items in the {@code other} Iterable
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param other
     *            the Iterable sequence
     * @param zipFunction
     *            a function that combines the pairs of items from the Flowable and the Iterable to generate
     *            the items to be emitted by the resulting Flowable
     * @return an Flowable that pairs up values from the source Flowable and the {@code other} Iterable
     *         sequence and emits the results of {@code zipFunction} applied to these pairs
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final <T2, R> Flowable<R> zipWith(Iterable<? extends T2> other, Func2<? super T, ? super T2, ? extends R> zipFunction) {
        return lift(new OperatorZipIterable<T, T2, R>(other, zipFunction));
    }

    /**
     * Returns an Flowable that emits items that are the result of applying a specified function to pairs of
     * values, one each from the source Flowable and another specified Flowable.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/zip.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code zipWith} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param <T2>
     *            the type of items emitted by the {@code other} Flowable
     * @param <R>
     *            the type of items emitted by the resulting Flowable
     * @param other
     *            the other Flowable
     * @param zipFunction
     *            a function that combines the pairs of items from the two Flowables to generate the items to
     *            be emitted by the resulting Flowable
     * @return an Flowable that pairs up values from the source Flowable and the {@code other} Flowable
     *         and emits the results of {@code zipFunction} applied to these pairs
     * @see <a href="http://reactivex.io/documentation/operators/zip.html">ReactiveX operators documentation: Zip</a>
     */
    public final <T2, R> Flowable<R> zipWith(Flowable<? extends T2> other, Func2<? super T, ? super T2, ? extends R> zipFunction) {
        return zip(this, other, zipFunction);
    }

    /**
     * An Flowable that never sends any information to an {@link Subscriber}.
     * This Flowable is useful primarily for testing purposes.
     * 
     * @param <T>
     *            the type of item (not) emitted by the Flowable
     */
    private static class NeverFlowable<T> extends Flowable<T> {
        public NeverFlowable() {
            super(new OnSubscribe<T>() {

                @Override
                public void call(Subscriber<? super T> observer) {
                    // do nothing
                }

            });
        }
    }

    /**
     * An Flowable that invokes {@link Subscriber#onError onError} when the {@link Subscriber} subscribes to it.
     * 
     * @param <T>
     *            the type of item (ostensibly) emitted by the Flowable
     */
    private static class ThrowFlowable<T> extends Flowable<T> {

        public ThrowFlowable(final Throwable exception) {
            super(new OnSubscribe<T>() {

                /**
                 * Accepts an {@link Subscriber} and calls its {@link Subscriber#onError onError} method.
                 * 
                 * @param observer
                 *            an {@link Subscriber} of this Flowable
                 */
                @Override
                public void call(Subscriber<? super T> observer) {
                    observer.onError(exception);
                }

            });
        }
    }
}
