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

package rx.subjects;

import static rx.internal.UnsafeAccess.*;

import java.util.*;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.disposables.Disposable;
import rx.exceptions.Exceptions;
import rx.internal.*;
import rx.internal.subscriptions.*;

/**
 * TODO javadoc with explanation about onSubscribe being optional
 */
public final class AsyncSubject<T> extends Subject<T, T> {
    /**
     * Creates and returns a new {@code AsyncSubject}.
     * @param <T> the result value type
     * @return the new {@code AsyncSubject}
     */
    public static <T> AsyncSubject<T> create() {
        SubjectSubscriberManager<T> psm = new SubjectSubscriberManager<>();
        OnSubscribe<T> onSubscribe = subscriber -> {
            ScalarBackpressureSubscription<T> sbs = new ScalarBackpressureSubscription<>(subscriber);
            CompositeDisposableSubscription ds = new CompositeDisposableSubscription(sbs);
            subscriber.onSubscribe(ds);
            
            if (!psm.add(subscriber)) {
                NotificationLite<T> nl = NotificationLite.instance();
                Object v = psm.get();
                if (v == null || nl.isCompleted(v)) {
                    sbs.onComplete();
                } else
                if (nl.isError(v)) {
                    sbs.onError(nl.getError(v));
                } else {
                    sbs.onNext(nl.getValue(v));
                }
            } else {
                ds.add(Disposable.from(() -> psm.remove(subscriber)));
            }
        };
        return new AsyncSubject<>(onSubscribe, psm);
    }
    
    final SubjectSubscriberManager<T> psm;
    final NotificationLite<T> nl = NotificationLite.instance();
    
    volatile Object lastValue;
    static final long LAST_VALUE = addressOf(AsyncSubject.class, "lastValue");
    
    /** Keeps the subscription to be able to report setting it multiple times. */
    Subscription subscription;
    
    private AsyncSubject(OnSubscribe<T> onSubscribe, SubjectSubscriberManager<T> psm) {
        super(onSubscribe);
        this.psm = psm;
    }
    @Override
    public void onSubscribe(Subscription subscription) {
        Conformance.subscriptionNonNull(subscription);
        Subscription curr = subscription;
        if (Conformance.onSubscribeOnce(curr, this)) {
            curr.cancel();
            return;
        }
        this.subscription = subscription;
        subscription.request(Long.MAX_VALUE);
    }
    @Override
    public void onNext(T item) {
        Conformance.itemNonNull(item);
        UNSAFE.putOrderedObject(this, LAST_VALUE, nl.next(item));
    }
    @Override
    public void onError(Throwable throwable) {
        Conformance.throwableNonNull(throwable);
        if (psm.active) {
            Object n = nl.error(throwable);
            List<Throwable> errors = null;
            for (Subscriber<? super T> bo : psm.terminate(n)) {
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
            for (Subscriber<? super T> bo : psm.terminate(last)) {
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
    @Override
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
    @Override
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

    @Override
    public int size() {
        return hasValue() ? 1 : 0;
    }
    
    @Override
    public boolean hasAnyValue() {
        return hasValue();
    }

    @Override
    public Object[] getValues() {
        Object v = lastValue;
        Object o = psm.get();
        if (!nl.isError(o) && nl.isNext(v)) {
            T value = nl.getValue(v);
            return new Object[] { value };
        }
        return new Object[0];
    }
    
    @Override
    public T[] getValues(T[] a) {
        Object v = lastValue;
        Object o = psm.get();
        if (!nl.isError(o) && nl.isNext(v)) {
            T value = nl.getValue(v);
            if (a.length == 0) {
                a = Arrays.copyOf(a, 1);
            }
            a[0] = value;
            if (a.length > 1) {
                a[1] = null;
            }
        } else
        if (a.length > 0) {
            a[0] = null;
        }
        return a;
    }
}
