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
package rxjf.processors;

import javax.security.auth.Subject;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.internal.subscriptions.DelayedSubscription;
import rxjf.subscribers.SerializedSubscriber;

/**
 * Wraps a {@link Subject} so that it is safe to call its various {@code on} methods from different threads.
 * <p>
 * When you use an ordinary {@link Subject} as a {@link Subscriber}, you must take care not to call its
 * {@link Subscriber#onNext} method (or its other {@code on} methods) from multiple threads, as this could lead
 * to non-serialized calls, which violates the Flowable contract and creates an ambiguity in the resulting
 * Subject.
 * <p>
 * To protect a {@code Subject} from this danger, you can convert it into a {@code SerializedSubject} with code
 * like the following:
 * <p><pre>{@code
 * mySafeSubject = new SerializedSubject( myUnsafeSubject );
 * }</pre>
 */
public final class SerializedProcessor<T, R> extends Flowable<R> implements ProcessorEx<T, R> {
    private final SerializedSubscriber<T> subscriber;
    private final ProcessorEx<T, R> actual;
    private final DelayedSubscription subscription;

    public SerializedProcessor(final ProcessorEx<T, R> actual) {
        super(new OnSubscribe<R>() {

            @Override
            @SuppressWarnings("unchecked")
            public void accept(Subscriber<? super R> child) {
                if (actual instanceof Flowable) {
                    ((Flowable<R>)actual).unsafeSubscribe(child);
                    return;
                }
                actual.subscribe(child);
            }

        });
        this.actual = actual;
        this.subscription = new DelayedSubscription(actual);
        this.subscriber = SerializedSubscriber.wrap(actual);
        this.subscriber.onSubscribe(subscription);
    }

    @Override
    public void onSubscribe(Subscription subscription) {
        this.subscription.setActual(subscription);
    }
    
    @Override
    public void onNext(T t) {
        subscriber.onNext(t);
    }

    @Override
    public void onError(Throwable e) {
        subscriber.onError(e);
    }

    @Override
    public void onComplete() {
        subscriber.onComplete();
    }

    @Override
    public boolean hasSubscribers() {
        return actual.hasSubscribers();
    }
    @Override
    public boolean hasComplete() {
        return actual.hasComplete();
    }
    @Override
    public Throwable getThrowable() {
        return actual.getThrowable();
    }
    @Override
    public boolean hasThrowable() {
        return actual.hasThrowable();
    }
    
    @Override
    public boolean hasAnyValue() {
        return actual.hasAnyValue();
    }
    @Override
    public R getValue() {
        return actual.getValue();
    }
    @Override
    public Object[] getValues() {
        return actual.getValues();
    }
    @Override
    public R[] getValues(R[] a) {
        return actual.getValues(a);
    }
    @Override
    public int size() {
        return actual.size();
    }
    
    @Override
    public ProcessorEx<T, R> toSerialized() {
        return this;
    }
}
