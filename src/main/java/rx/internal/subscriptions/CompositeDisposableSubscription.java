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
package rx.internal.subscriptions;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.disposables.*;
import rx.internal.Conformance;

/**
 * Subscription that wraps another Subscription, manages a collection of Disposable resources
 * and disposes them on cancel.
 * <p>
 * Note: this Subscription doesn't do conformance checks in its request() method and doesn't
 * alter the concurrency properties of the underlying actual subscription.
 */
public final class CompositeDisposableSubscription implements Subscription, Disposable {
    /** The composite tracking Disposable resources. */
    final CompositeDisposable composite;
    /**
     * The actual subscription where requests are forwarded to.
     */
    final Subscription actual;
    /**
     * Constructs a DisposableSubscription by wrapping the given non-null Subscription.
     * @param actual the actual subscription to wrap
     */
    public CompositeDisposableSubscription(Subscription actual) {
        this.actual = Conformance.subscriptionNonNull(actual);
        this.composite = new CompositeDisposable();
    }
    @Override
    public void cancel() {
        composite.dispose();
        actual.cancel();
    }
    @Override
    public void dispose() {
        cancel();
    }
    @Override
    public boolean isDisposed() {
        return composite.isDisposed();
    }
    @Override
    public void request(long n) {
        actual.request(n);
    }
    
    
    /**
     * Adds a new {@link Disposable} to this {@code DisposableSubscription} if the
     * {@code CompositeDisposable} is not yet disposed. If the {@code CompositeDisposable} <em>is</em>
     * unsubscribed, {@code add} will indicate this by explicitly disposing the new {@code Disposable} as
     * well.
     *
     * @param s
     *          the {@link Disposable} to add
     */
    public void add(Disposable disposable) {
        composite.add(disposable);
    }
    /**
     * Constructs a DisposableSubscription with an empty actual subscription.
     * @param subscriber the subscriber to report conformance errors to
     * @return the created DisposableSubscription
     */
    public static CompositeDisposableSubscription createEmpty(Subscriber<?> subscriber) {
        return new CompositeDisposableSubscription(AbstractSubscription.createEmpty(subscriber));
    }
}
