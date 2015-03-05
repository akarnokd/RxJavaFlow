/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.operators;

import static rx.internal.UnsafeAccess.*;
import rx.Flow.*;
import rx.*;
import rx.Observable.Operator;
import rx.disposables.Disposable;
import rx.internal.Conformance;
import rx.internal.operators.OperatorTakeUntil.*;
import rx.internal.subscriptions.SingleDisposableSubscription;
import rx.subscribers.*;

/**
 * Skip elements from the source Observable until the secondary
 * observable fires an element.
 * 
 * If the secondary Observable fires no elements, the primary won't fire any elements.
 * 
 * @see <a href='http://msdn.microsoft.com/en-us/library/hh229358.aspx'>MSDN: Observable.SkipUntil</a>
 * 
 * @param <T> the source and result value type
 * @param <U> element type of the signalling observable
 */
public final class OperatorSkipUntil<T, U> implements Operator<T, T> {
    final Observable<U> other;

    public OperatorSkipUntil(Observable<U> other) {
        this.other = other;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {
        final SerializedSubscriber<? super T> s = SerializedSubscriber.wrap(child);
        
        return new SkipUntilSubscriber<>(s, other);
    }
    
    static final class SkipUntilSubscriber<T> implements Subscriber<T> {
        final Subscriber<? super T> actual;
        final Observable<?> other;
        SingleDisposableSubscription subscription;
        
        /** The gate state: -1: not yet open, 0: open, 1: closed. */
        volatile int gate;
        static final long GATE = addressOf(TakeUntilSubscriber.class, "gate");
        
        static final int STATE_SKIPPING = -1;
        static final int STATE_OPEN = 0;
        static final int STATE_CLOSED = 1;
        
        public SkipUntilSubscriber(Subscriber<? super T> actual, Observable<?> other) {
            this.actual = actual;
            this.other = other;
            UNSAFE.putOrderedInt(this, GATE, STATE_SKIPPING);
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
            
            actual.onSubscribe(s);
        }
        void open() {
            if (gate == STATE_SKIPPING) {
                UNSAFE.compareAndSwapInt(this, GATE, STATE_SKIPPING, STATE_OPEN); 
            }
        }
        @Override
        public void onNext(T item) {
            if (gate == STATE_OPEN) {
                actual.onNext(item);
            }
        }
        @Override
        public void onError(Throwable throwable) {
            if (UNSAFE.getAndSetInt(this, GATE, STATE_CLOSED) != STATE_CLOSED) {
                actual.onError(throwable);
            }
        }
        @Override
        public void onComplete() {
            if (UNSAFE.getAndSetInt(this, GATE, STATE_CLOSED) != STATE_CLOSED) {
                actual.onComplete();
            }
        }
    }
    /**
     * Subscribed to the other observable.
     */
    static final class OtherSubscriber extends AbstractSubscriber<Object> {
        final SkipUntilSubscriber<?> parent;
        public OtherSubscriber(SkipUntilSubscriber<?> parent) {
            this.parent = parent;
        }
        @Override
        public void onNext(Object item) {
            try {
                parent.open();
            } finally {
                subscription.cancel();
            }
        }
        @Override
        public void onError(Throwable throwable) {
            try {
                parent.onError(throwable);
            } finally {
                subscription.cancel();
            }
        }
        @Override
        public void onComplete() {
            try {
                parent.open();
            } finally {
                subscription.cancel();
            }
        }
    }

}
