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

package rxjf.processors;

import static rxjf.internal.UnsafeAccess.*;

import java.util.*;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.exceptions.Exceptions;
import rxjf.internal.*;
import rxjf.internal.operators.OnSubscribe;

/**
 * 
 */
public final class AsyncProcessor<T> extends Flowable<T> implements ProcessorEx<T, T> {
    public static <T> AsyncProcessor<T> create() {
        ProcessorSubscriptionManager<T> psm = new ProcessorSubscriptionManager<>();
        OnSubscribe<T> onSubscribe = subscriber -> {
            NotificationLite<T> nl = NotificationLite.instance();
            // FIXME this ignores the request amount and emits regardless
            subscriber.onSubscribe(AbstractSubscription.createEmpty(subscriber));
            if (!psm.add(subscriber)) {
                Object v = psm.get();
                nl.accept(subscriber, v);
                if (v == null || (!nl.isCompleted(v) && !nl.isError(v))) {
                    subscriber.onComplete();
                }
            }
        };
        return new AsyncProcessor<>(onSubscribe, psm);
    }
    
    final ProcessorSubscriptionManager<T> psm;
    final NotificationLite<T> nl = NotificationLite.instance();
    
    volatile Object lastValue;
    static final long LAST_VALUE = addressOf(AsyncProcessor.class, "lastValue");
    
    private AsyncProcessor(OnSubscribe<T> onSubscribe, ProcessorSubscriptionManager<T> psm) {
        super(onSubscribe);
        this.psm = psm;
    }
    @Override
    public void onSubscribe(Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }
    @Override
    public void onNext(T item) {
        UNSAFE.putOrderedObject(this, LAST_VALUE, nl.next(item));
    }
    @Override
    public void onError(Throwable throwable) {
        if (psm.active) {
            Object n = nl.error(throwable);
            List<Throwable> errors = null;
            for (Subscriber<T> bo : psm.terminate(n)) {
                try {
                    bo.onError(throwable);
                } catch (Throwable e2) {
                    if (errors == null) {
                        errors = new ArrayList<>();
                    }
                    errors.add(e2);
                }
            }

            Exceptions.throwIfAny(errors);
        }
    }
    @Override
    public void onComplete() {
        if (psm.active) {
            Object last = lastValue;
            if (last == null) {
                last = nl.complete();
            }
            for (Subscriber<T> bo : psm.terminate(last)) {
                if (last == nl.complete()) {
                    bo.onComplete();
                } else {
                    bo.onNext(nl.getValue(last));
                    bo.onComplete();
                }
            }
        }
    }
    @Override
    public boolean hasSubscribers() {
        return psm.subscribers().length != 0;
    }
    /**
     * Check if the Subject has a value.
     * <p>Use the {@link #getValue()} method to retrieve such a value.
     * <p>Note that unless {@link #hasCompleted()} or {@link #hasThrowable()} returns true, the value
     * retrieved by {@code getValue()} may get outdated.
     * @return true if and only if the subject has some value but not an error
     */
    public boolean hasValue() {
        Object v = lastValue;
        Object o = psm.get();
        return !nl.isError(o) && nl.isNext(v);
    }
    /**
     * Check if the Subject has terminated with an exception.
     * @return true if the subject has received a throwable through {@code onError}.
     */
    @Override
    public boolean hasThrowable() {
        Object o = psm.get();
        return nl.isError(o);
    }
    /**
     * Check if the Subject has terminated normally.
     * @return true if the subject completed normally via {@code onComplete()}
     */
    @Override
    public boolean hasComplete() {
        Object o = psm.get();
        return o != null && !nl.isError(o);
    }
    /**
     * Returns the current value of the Subject if there is such a value and
     * the subject hasn't terminated with an exception.
     * <p>The can return {@code null} for various reasons. Use {@link #hasValue()}, {@link #hasThrowable()}
     * and {@link #hasCompleted()} to determine if such {@code null} is a valid value, there was an
     * exception or the Subject terminated without receiving any value. 
     * @return the current value or {@code null} if the Subject doesn't have a value,
     * has terminated with an exception or has an actual {@code null} as a value.
     */
    public T getValue() {
        Object v = lastValue;
        Object o = psm.get();
        if (!nl.isError(o) && nl.isNext(v)) {
            return nl.getValue(v);
        }
        return null;
    }
    /**
     * Returns the Throwable that terminated the Subject.
     * @return the Throwable that terminated the Subject or {@code null} if the
     * subject hasn't terminated yet or it terminated normally.
     */
    @Override
    public Throwable getThrowable() {
        Object o = psm.get();
        if (nl.isError(o)) {
            return nl.getError(o);
        }
        return null;
    }

}
