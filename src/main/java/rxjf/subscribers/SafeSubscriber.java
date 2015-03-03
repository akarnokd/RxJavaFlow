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
package rxjf.subscribers;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.Conformance;

/**
 *
 */
public final class SafeSubscriber<T> implements Subscriber<T> {
    final Subscriber<? super T> actual;
    private SafeSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    public static <T> Subscriber<T> wrap(Subscriber<T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        if (subscriber instanceof SafeSubscriber) {
            return subscriber;
        }
        if (subscriber instanceof SerializedSubscriber) {
            return subscriber;
        }
        return new SafeSubscriber<>(subscriber);
    }
    boolean done;
    Subscription subscription;
    @Override
    public void onSubscribe(Subscription subscription) {
        Conformance.subscriptionNonNull(subscription);
        Subscription current = this.subscription;
        if (!Conformance.onSubscribeOnce(current, this)) {
            current.cancel();
            return;
        }
        this.subscription = subscription;
        actual.onSubscribe(subscription);
    }
    @Override
    public void onNext(T item) {
        if (!done) {
            Conformance.subscriptionNonNull(subscription);
            Conformance.itemNonNull(item);
            try {
                actual.onNext(item);
            } catch (Throwable t) {
                onError(t);
            }
        }
    }
    @Override
    public void onError(Throwable throwable) {
        if (!done) {
            Subscription s = subscription;
            Conformance.subscriptionNonNull(s);
            Conformance.throwableNonNull(throwable);
            done = true;
            try {
                actual.onError(throwable);
            } catch (Throwable t) {
                handleUncaught(t);
            } finally {
                s.cancel();
            }
        }
    }
    @Override
    public void onComplete() {
        if (!done) {
            Subscription s = subscription;
            Conformance.subscriptionNonNull(s);
            done = true;
            try {
                actual.onComplete();
            } catch (Throwable t) {
                handleUncaught(t);
            } finally {
                s.cancel();
            }
        }
    }
    
    private void handleUncaught(Throwable t) {
        Thread ct = Thread.currentThread();
        ct.getUncaughtExceptionHandler().uncaughtException(ct, t);
    }
}
