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
import java.util.concurrent.TimeUnit;
import java.util.function.*;

import rxjf.Flow.Publisher;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.disposables.Disposable;
import rxjf.exceptions.*;
import rxjf.internal.*;
import rxjf.internal.operators.*;
import rxjf.schedulers.*;
import rxjf.subscribers.*;

/**
 *
 */
public class Flowable<T> implements Publisher<T> {
    final Consumer<Subscriber<? super T>> onSubscribe;
    protected Flowable(Consumer<Subscriber<? super T>> onSubscribe) {
        this.onSubscribe = Objects.requireNonNull(onSubscribe);
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
    
    public static <T> Flowable<T> create(Consumer<Subscriber<? super T>> onSubscribe) {
        return new Flowable<>(onSubscribe);
    }
    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        unsafeSubscribe(SafeSubscriber.wrap(subscriber));
    }
    public final void unsafeSubscribe(Subscriber<? super T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        try {
            onSubscribe.accept(subscriber);
        } catch (Throwable t) {
            try {
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
     * In other words, this allows chaining Observers together on an Flowable for acting on the values within
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
                Subscriber<? super T> s2 = lifter.apply(s);
                onSubscribe.accept(s2);
            } catch (Throwable e) {
                try {
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
     * Subscribers or Observers.
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
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the Flowable has finished sending them
     * @throws OnErrorNotImplementedException
     *             if the Flowable tries to call {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final Disposable subscribe() {
        DisposableSubscriber<Object> cs = EMPTY_SUBSCRIBER.toDisposable();
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
     *             the {@code Action1<T>} you have designed to accept emissions from the Flowable
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the Flowable has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null
     * @throws OnErrorNotImplementedException
     *             if the Flowable tries to call {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final Disposable subscribe(Consumer<? super T> onNext) {
        Objects.requireNonNull(onNext);
        DisposableSubscriber<T> cs = new AbstractSubscriber<T>() {
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
     *             the {@code Action1<T>} you have designed to accept emissions from the Flowable
     * @param onError
     *             the {@code Action1<Throwable>} you have designed to accept any error notification from the
     *             Flowable
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the Flowable has finished sending them
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null
     */
    public final Disposable subscribe(Consumer<? super T> onNext, Consumer<Throwable> onError) {
        Objects.requireNonNull(onNext);
        Objects.requireNonNull(onError);
        DisposableSubscriber<T> cs = new AbstractSubscriber<T>() {
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
     *             the {@code Action1<T>} you have designed to accept emissions from the Flowable
     * @param onError
     *             the {@code Action1<Throwable>} you have designed to accept any error notification from the
     *             Flowable
     * @param onComplete
     *             the {@code Action0} you have designed to accept a completion notification from the
     *             Flowable
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
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
        DisposableSubscriber<T> cs = new AbstractSubscriber<T>() {
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
    
    public final Disposable subscribeDisposable(Subscriber<? super T> subscriber) {
        DisposableSubscriber<? super T> cs = DisposableSubscriber.wrap(subscriber);
        subscribe(cs);
        return cs;
    }

    // -----------------------------------------------------------
    // -  OPERATORS ----------------------------------------------
    // -----------------------------------------------------------
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
     * Returns an Flowable that emits no items to the {@link Observer} and immediately invokes its
     * {@link Observer#onCompleted onCompleted} method.
     * <p>
     * <img width="640" height="190" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/empty.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code empty} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     *
     * @param <T>
     *            the type of the items (ostensibly) emitted by the Flowable
     * @return an Flowable that emits no items to the {@link Observer} but immediately invokes the
     *         {@link Observer}'s {@link Observer#onCompleted() onCompleted} method
     * @see <a href="http://reactivex.io/documentation/operators/empty-never-throw.html">ReactiveX operators documentation: Empty</a>
     */
    @SuppressWarnings("unchecked")
    public static <T> Flowable<T> empty() {
        return (Flowable<T>)EMPTY;
    }
    static final Flowable<Object> NEVER = create(new OnSubscribeNever<>());
    /**
     * Returns an Flowable that never sends any items or notifications to an {@link Observer}.
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
     * @return an Flowable that never emits any items or sends any notifications to an {@link Observer}
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
     * This method returns an Flowable that will invoke a subscribing {@link Observer}'s
     * {@link Observer#onNext onNext} function a maximum of {@code num} times before invoking
     * {@link Observer#onCompleted onCompleted}.
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
        if (start >= 0 && start + count < 0) {
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
        if (start >= 0 && start + count < 0) {
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
        return new BlockingFlowable<>(this);
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
     *            the {@link Scheduler} to notify {@link Observer}s on
     * @return the source Flowable modified so that its {@link Observer}s are notified on the specified
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
     * Asynchronously subscribes Observers to this Flowable on the specified {@link Scheduler}.
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
     * Subscribes to the {@link Flowable} and receives notifications for each element.
     * <p>
     * Alias to {@link #subscribe(Action1)}
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code forEach} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *            {@link Action1} to execute for each item.
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
     * Returns a {@link ConnectableFlowable}, which is a variety of Flowable that waits until its
     * {@link ConnectableFlowable#connect connect} method is called before it begins emitting items to those
     * {@link Observer}s that have subscribed to it.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link ConnectableFlowable} that upon connection causes the source Flowable to emit items
     *         to its {@link Observer}s
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    public final ConnectableFlowable<T> publish() {
        // TODO
        throw new UnsupportedOperationException();
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
     * Flattens an Flowable that emits Flowables into one Flowable, in a way that allows an Observer to
     * receive all successfully emitted items from all of the source Flowables without being interrupted by
     * an error notification from one of them.
     * <p>
     * This behaves like {@link #merge(Flowable)} except that if any of the merged Flowables notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
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
    public final static <T> Flowable<T> merge(Flowable<? extends T>[] sequences) {
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
     * Flattens an Flowable that emits Flowables into one Flowable, in a way that allows an Observer to
     * receive all successfully emitted items from all of the source Flowables without being interrupted by
     * an error notification from one of them, while limiting the
     * number of concurrent subscriptions to these Flowables.
     * <p>
     * This behaves like {@link #merge(Flowable)} except that if any of the merged Flowables notify of an
     * error via {@link Observer#onError onError}, {@code mergeDelayError} will refrain from propagating that
     * error notification until all of the merged Flowables have finished emitting items.
     * <p>
     * <img width="640" height="380" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/mergeDelayError.png" alt="">
     * <p>
     * Even if multiple merged Flowables send {@code onError} notifications, {@code mergeDelayError} will only
     * invoke the {@code onError} method of its Observers once.
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
     * Normally, an Flowable that returns multiple items will do so by invoking its {@link Observer}'s
     * {@link Observer#onNext onNext} method for each such item. You can change this behavior, instructing the
     * Flowable to compose a list of all of these items and then to invoke the Observer's {@code onNext}
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
     * Modifies the source Flowable so that it notifies an Observer for each item it emits.
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
     * Returns an Flowable that invokes an {@link Observer}'s {@link Observer#onError onError} method when the
     * Observer subscribes to it.
     * <p>
     * <img width="640" height="190" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/error.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code error} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param exception
     *            the particular Throwable to pass to {@link Observer#onError onError}
     * @param <T>
     *            the type of the items (ostensibly) emitted by the Flowable
     * @return an Flowable that invokes the {@link Observer}'s {@link Observer#onError onError} method when
     *         the Observer subscribes to it
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
     *            result will be emitted to {@link Observer}s via {@link Observer#onNext onNext} and used in the
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
     *            result will be emitted to {@link Observer}s via {@link Observer#onNext onNext} and used in the
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
}
