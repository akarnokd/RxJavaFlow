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
import rxjf.cancellables.Cancellable;
import rxjf.exceptions.OnErrorNotImplementedException;
import rxjf.internal.Conformance;
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
    public final <R> Flowable<R> lift(Function<Subscriber<? super R>, Subscriber<? super T>> lifter) {
        Objects.requireNonNull(lifter);
        return create(s -> {
            try {
                Subscriber<? super T> s2 = lifter.apply(s);
                onSubscribe.accept(s2);
            } catch (Throwable e) {
                try {
                    s.onError(e);
                } catch (Throwable e2) {
                    handleUncaught(e2);
                }
            }
        });
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
    
    public final Cancellable subscribe() {
        CancellableSubscriber<Object> cs = EMPTY_SUBSCRIBER.toCancellable();
        subscribe(cs);
        return cs;
    }

    /**
     * Subscribes to an Observable and provides a callback to handle the items it emits.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Action1<T>} you have designed to accept emissions from the Observable
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the Observable has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null
     * @throws OnErrorNotImplementedException
     *             if the Observable tries to call {@code onError}
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final Cancellable subscribe(Consumer<? super T> onNext) {
        CancellableSubscriber<T> cs = new AbstractSubscriber<T>() {
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
        }.toCancellable();
        subscribe(cs);
        return cs;
    }

    /**
     * Subscribes to an Observable and provides callbacks to handle the items it emits and any error
     * notification it issues.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Action1<T>} you have designed to accept emissions from the Observable
     * @param onError
     *             the {@code Action1<Throwable>} you have designed to accept any error notification from the
     *             Observable
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the Observable has finished sending them
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null
     */
    public final Cancellable subscribe(Consumer<? super T> onNext, Consumer<Throwable> onError) {
        CancellableSubscriber<T> cs = new AbstractSubscriber<T>() {
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
        }.toCancellable();
        subscribe(cs);
        return cs;
    }

    /**
     * Subscribes to an Observable and provides callbacks to handle the items it emits and any error or
     * completion notification it issues.
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code subscribe} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @param onNext
     *             the {@code Action1<T>} you have designed to accept emissions from the Observable
     * @param onError
     *             the {@code Action1<Throwable>} you have designed to accept any error notification from the
     *             Observable
     * @param onComplete
     *             the {@code Action0} you have designed to accept a completion notification from the
     *             Observable
     * @return a {@link Subscription} reference with which the {@link Observer} can stop receiving items before
     *         the Observable has finished sending them
     * @throws IllegalArgumentException
     *             if {@code onNext} is null, or
     *             if {@code onError} is null, or
     *             if {@code onComplete} is null
     * @see <a href="http://reactivex.io/documentation/operators/subscribe.html">ReactiveX operators documentation: Subscribe</a>
     */
    public final Cancellable subscribe(Consumer<? super T> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        CancellableSubscriber<T> cs = new AbstractSubscriber<T>() {
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
        }.toCancellable();
        subscribe(cs);
        return cs;
    }
    
    public final Cancellable subscribeCancellable(Subscriber<? super T> subscriber) {
        CancellableSubscriber<? super T> cs = CancellableSubscriber.wrap(subscriber);
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
        return create(new OnSubscribeArray<>(Objects.requireNonNull(values)));
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
        return null;
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
     * Subscribes to the {@link Observable} and receives notifications for each element.
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
     * Returns a {@link ConnectableObservable}, which is a variety of Observable that waits until its
     * {@link ConnectableObservable#connect connect} method is called before it begins emitting items to those
     * {@link Observer}s that have subscribed to it.
     * <p>
     * <img width="640" height="510" src="https://raw.github.com/wiki/ReactiveX/RxJava/images/rx-operators/publishConnect.png" alt="">
     * <dl>
     *  <dt><b>Scheduler:</b></dt>
     *  <dd>{@code publish} does not operate by default on a particular {@link Scheduler}.</dd>
     * </dl>
     * 
     * @return a {@link ConnectableObservable} that upon connection causes the source Observable to emit items
     *         to its {@link Observer}s
     * @see <a href="http://reactivex.io/documentation/operators/publish.html">ReactiveX operators documentation: Publish</a>
     */
    public final ConnectableFlowable<T> publish() {
        // TODO
        throw new UnsupportedOperationException();
    }
    
}
