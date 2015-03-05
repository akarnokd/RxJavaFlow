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
package rx.internal.operators;

import static rx.internal.UnsafeAccess.*;
import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.*;
import rx.Observable.Operator;
import rx.disposables.Disposable;
import rx.internal.Conformance;
import rx.internal.subscriptions.SingleDisposableSubscription;
import rx.subscribers.*;

/**
 * Returns an Observable that emits the items from the source Observable until another Observable
 * emits an item.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/takeUntil.png" alt="">
 */
public final class OperatorTakeUntil<T, E> implements Operator<T, T> {

    final Observable<? extends E> other;

    public OperatorTakeUntil(final Observable<? extends E> other) {
        this.other = other;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> child) {
        final SerializedSubscriber<? super T> s = SerializedSubscriber.wrap(child);
        
        return new TakeUntilSubscriber<>(s, other);
    }
    
    /**
     * Subscribed to the main source.
     * @param <T> the element type
     */
    static final class TakeUntilSubscriber<T> implements Subscriber<T> {
        final Observable<?> other;
        final Subscriber<? super T> child;
        SingleDisposableSubscription subscription;
        
        volatile int gate;
        static final long GATE = addressOf(TakeUntilSubscriber.class, "gate");
        
        public TakeUntilSubscriber(Subscriber<? super T> child, Observable<?> other) {
            this.child = child;
            this.other = other;
        }
        @Override
        public void onSubscribe(Subscription subscription) {
            Conformance.subscriptionNonNull(subscription);
            if (!Conformance.onSubscribeOnce(this.subscription, this)) {
                subscription.cancel();
                return;
            }
            SingleDisposableSubscription s = new SingleDisposableSubscription(subscription);
            this.subscription = s;
            
            Disposable d = other.unsafeSubscribeDisposable(new OtherSubscriber(this));
            
            s.set(d);
            
            child.onSubscribe(s);
        }
        @Override
        public void onNext(T item) {
            if (gate == 0) {
                child.onNext(item);
            }
        }
        @Override
        public void onError(Throwable throwable) {
            if (UNSAFE.getAndSetInt(this, GATE, 1) == 0) {
                try {
                    child.onError(throwable);
                } finally {
                    subscription.cancel();
                }
            }
        }
        @Override
        public void onComplete() {
            if (UNSAFE.getAndSetInt(this, GATE, 1) == 0) {
                try {
                    child.onComplete();
                } finally {
                    subscription.cancel();
                }
            }
        }
    }
    
    /**
     * Subscribed to the other observable and signals onComplete to the parent
     * when onComplete is called.
     */
    static final class OtherSubscriber extends AbstractSubscriber<Object> {
        final Subscriber<?> parent;
        public OtherSubscriber(Subscriber<?> parent) {
            this.parent = parent;
        }
        @Override
        public void onNext(Object item) {
            parent.onComplete();
        }
        @Override
        public void onError(Throwable throwable) {
            parent.onError(throwable);
        }
        @Override
        public void onComplete() {
            parent.onComplete();
        }
    }
}
