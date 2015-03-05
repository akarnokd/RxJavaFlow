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
package rx.subscribers;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.exceptions.Exceptions;
import rx.internal.Conformance;
import rx.plugins.RxJavaPlugins;

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
    public void onSubscribe(Subscription s) {
        Conformance.subscriptionNonNull(s);
        Subscription current = this.subscription;
        if (!Conformance.onSubscribeOnce(current, this)) {
            try {
                s.cancel();
            } catch (Throwable t) {
                handleUncaught(Conformance.cancelThrew(t));
            }
            return;
        }
        this.subscription = new Subscription() {
            @Override
            public void request(long n) {
                if (!Conformance.requestPositive(n, SafeSubscriber.this)) {
                    return;
                }
                try {
                    s.request(n);
                } catch (Throwable t) {
                    onError(Conformance.requestThrew(t));
                }
            }
            @Override
            public void cancel() {
                try {
                    s.cancel();
                } catch (Throwable t) {
                    handleUncaught(Conformance.cancelThrew(t));
                }
            }
        };
        try {
            actual.onSubscribe(subscription);
        } catch (NullPointerException npe) {
            throw npe;
        } catch (Throwable t) {
            handleUncaught(Conformance.onSubscribeThrew(t));
        }
    }
    @Override
    public void onNext(T item) {
        if (!done) {
            Conformance.subscriptionNonNull(subscription);
            Conformance.itemNonNull(item);
            try {
                actual.onNext(item);
            } catch (NullPointerException npe) {
                throw npe;
            } catch (Throwable t) {
                onError(Conformance.onNextThrew(t));
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
                try {
                    actual.onError(throwable);
                } catch (NullPointerException npe) {
                    throw npe;
                } catch (Throwable t) {
                    handleUncaught(Conformance.onErrorThrew(t));
                }
            } finally {
                subscription.cancel();
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
                try {
                    actual.onComplete();
                } catch (NullPointerException npe) {
                    throw npe;
                } catch (Throwable t) {
                    handleUncaught(Conformance.onCompleteThrew(t));
                }
            } finally {
                subscription.cancel();
            }
        }
    }
    /**
     * Handles the any uncaught exceptions.
     * @param t the uncaught exception
     */
    private void handleUncaught(Throwable t) {
        try {
            Exceptions.handleUncaught(t);
            RxJavaPlugins.getInstance().getErrorHandler().handleError(t);
        } catch (Throwable e) {
            // nowhere to go now
        }
    }
}
