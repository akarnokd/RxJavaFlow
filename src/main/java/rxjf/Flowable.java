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

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

import rxjf.Flow.Publisher;
import rxjf.Flow.Subscriber;
import rxjf.cancellables.Cancellable;
import rxjf.internal.Conformance;
import rxjf.internal.operators.*;
import rxjf.schedulers.*;
import rxjf.subscribers.SafeSubscriber;

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
    void handleUncaught(Throwable t) {
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
    
    public final Cancellable subscribe() {
        // TODO
        return null;
    }

    public final Cancellable subscribe(Consumer<? super T> onNext) {
        // TODO
        return null;
    }

    public final Cancellable subscribe(Consumer<? super T> onNext, Consumer<Throwable> onError) {
        // TODO
        return null;
    }

    public final Cancellable subscribe(Consumer<? super T> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        // TODO
        return null;
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
    public static <T> Flowable<T> just(T value) {
        return ScalarSynchronousFlow.create(Conformance.itemNonNull(value));
    }
    
    public static <T> Flowable<T> from(Iterable<? extends T> source) {
        return create(new OnSubscribeIterable<>(Objects.requireNonNull(source)));
    }
    @SafeVarargs
    public static <T> Flowable<T> from(T... values) {
        return create(new OnSubscribeArray<>(Objects.requireNonNull(values)));
    }
    
    public final <R> Flowable<R> map(Function<? super T, ? extends R> function) {
        return lift(new OperatorMap<>(function));
    }
    static final Flowable<Object> EMPTY = create(new OnSubscribeEmpty<>());
    @SuppressWarnings("unchecked")
    public static <T> Flowable<T> empty() {
        return (Flowable<T>)EMPTY;
    }
    static final Flowable<Object> NEVER = create(new OnSubscribeNever<>());
    @SuppressWarnings("unchecked")
    public static <T> Flowable<T> never() {
        return (Flowable<T>)NEVER;
    }
    
    public final Flowable<T> take(long n) {
        return lift(new OperatorTake<>(n));
    }
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
    public final Flowable<T> timeout(long timeout, TimeUnit unit) {
        return timeout(timeout, unit, Schedulers.computation());
    }
    public final Flowable<T> timeout(long timeout, TimeUnit unit, Scheduler scheduler) {
        // TODO
        return null;
    }
    public final BlockingFlowable<T> toBlocking() {
        return new BlockingFlowable<>(this);
    }
}
