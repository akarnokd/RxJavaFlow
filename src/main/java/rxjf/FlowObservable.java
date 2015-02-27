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

import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.function.*;

/**
 *
 */
public class FlowObservable<T> implements Publisher<T> {
    final Consumer<Subscriber<? super T>> onSubscribe;
    protected FlowObservable(Consumer<Subscriber<? super T>> onSubscribe) {
        if (onSubscribe == null) {
            throw new NullPointerException();
        }
        this.onSubscribe = onSubscribe;
    }
    public static <T> FlowObservable<T> create(Consumer<Subscriber<? super T>> onSubscribe) {
        return new FlowObservable<>(onSubscribe);
    }
    @Override
    public final void subscribe(Subscriber<? super T> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException();
        }
        try {
            onSubscribe.accept(subscriber);
        } catch (Throwable t) {
            handleUncaught(t);
        }
    }
    public final void safeSubscribe(Subscriber<? super T> subscriber) {
        subscribe(SafeSubscriber.wrap(subscriber));
    }
    void handleUncaught(Throwable t) {
        Thread currentThread = Thread.currentThread();
        currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, t);
    }
    public final <R> FlowObservable<R> lift(Function<Subscriber<? super R>, Subscriber<? super T>> lifter) {
        if (lifter == null) {
            throw new NullPointerException();
        }
        return create(s -> lifter.apply(s));
    }
}
