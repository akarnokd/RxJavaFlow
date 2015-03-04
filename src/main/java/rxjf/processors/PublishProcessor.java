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

import java.util.*;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.disposables.Disposable;
import rxjf.exceptions.Exceptions;
import rxjf.internal.*;
import rxjf.internal.subscriptions.*;

/**
 * TODO javadoc and explaing onSubscribe being optional.
 */
public final class PublishProcessor<T> extends Flowable<T> implements ProcessorEx<T, T> {
    /**
     * Creates and returns a new {@code PublishSubject}.
     *
     * @param <T> the value type
     * @return the new {@code PublishSubject}
     */
    public static <T> PublishProcessor<T> create() {
        ProcessorSubscriberManager<T> psm = new ProcessorSubscriberManager<>();
        OnSubscribe<T> onSubscribe = subscriber -> {
            Subscription empty = AbstractSubscription.createEmpty(subscriber);
            // FIXME only a single disposable is ever added, a composite is unnecessary
            CompositeDisposableSubscription ds = new CompositeDisposableSubscription(empty);
            
            subscriber.onSubscribe(ds);
            
            if (!psm.add(subscriber)) {
                NotificationLite<T> nl = NotificationLite.instance();
                Object v = psm.get();

                if (nl.isCompleted(v)) {
                    subscriber.onComplete();
                } else
                if (nl.isError(v)) {
                    subscriber.onError(nl.getError(v));
                } else {
                    subscriber.onError(new IllegalStateException("PublishProcessor has a value instead of terminal event: " + v));
                }
            } else {
                ds.add(Disposable.from(() -> psm.remove(subscriber)));
            }
        };
        return new PublishProcessor<>(onSubscribe, psm);
    }
    
    final ProcessorSubscriberManager<T> psm;
    private final NotificationLite<T> nl = NotificationLite.instance();
    /** Keeps the subscription to be able to report setting it multiple times. */
    Subscription subscription;
    
    protected PublishProcessor(OnSubscribe<T> onSubscribe, ProcessorSubscriberManager<T> psm) {
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
        for (Subscriber<? super T> bo : psm.subscribers()) {
            // FIXME if onNext throws, the error should be bounced back and bo evicted
            bo.onNext(item);
        }
    }
    @Override
    public void onError(Throwable throwable) {
        Conformance.throwableNonNull(throwable);
        if (psm.active) {
            Object n = nl.error(throwable);
            List<Throwable> errors = null;
            for (Subscriber<? super T> bo : psm.terminate(n)) {
                try {
                    // bo.emitNext(n, nl);
                    // FIXME if onNext throws, the error should be reported and bo evicted
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
            Object n = nl.complete();
            for (Subscriber<? super T> bo : psm.terminate(n)) {
                // bo.emitNext(n, nl);
                // FIXME if onNext throws, the error should be reported and bo evicted
                bo.onComplete();
            }
        }
    }
    @Override
    public boolean hasComplete() {
        Object o = psm.get();
        return o != null && !nl.isError(o);
    }
    @Override
    public boolean hasSubscribers() {
        return psm.subscribers().length > 0;
    }
    @Override
    public Throwable getThrowable() {
        Object o = psm.get();
        if (nl.isError(o)) {
            return nl.getError(o);
        }
        return null;
    }
    @Override
    public boolean hasThrowable() {
        Object o = psm.get();
        return nl.isError(o);
    }
}
