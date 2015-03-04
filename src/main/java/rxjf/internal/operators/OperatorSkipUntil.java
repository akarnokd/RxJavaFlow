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
package rxjf.internal.operators;

import static rxjf.internal.UnsafeAccess.*;
import rxjf.Flow.*;
import rxjf.*;
import rxjf.Flowable.Operator;
import rxjf.disposables.Disposable;
import rxjf.internal.Conformance;
import rxjf.internal.operators.OperatorTakeUntil.*;
import rxjf.internal.subscriptions.SingleDisposableSubscription;
import rxjf.subscribers.*;

/**
 * Skip elements from the source Flowable until the secondary
 * observable fires an element.
 * 
 * If the secondary Flowable fires no elements, the primary won't fire any elements.
 * 
 * @see <a href='http://msdn.microsoft.com/en-us/library/hh229358.aspx'>MSDN: Flowable.SkipUntil</a>
 * 
 * @param <T> the source and result value type
 * @param <U> element type of the signalling observable
 */
public final class OperatorSkipUntil<T, U> implements Operator<T, T> {
    final Flowable<U> other;

    public OperatorSkipUntil(Flowable<U> other) {
        this.other = other;
    }

    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {
        final SerializedSubscriber<? super T> s = SerializedSubscriber.wrap(child);
        
        return new SkipUntilSubscriber<>(s, other);
    }
    
    static final class SkipUntilSubscriber<T> implements Subscriber<T> {
        final Subscriber<? super T> actual;
        final Flowable<?> other;
        SingleDisposableSubscription subscription;
        
        volatile int gate;
        static final long GATE = addressOf(TakeUntilSubscriber.class, "gate");
        public SkipUntilSubscriber(Subscriber<? super T> actual, Flowable<?> other) {
            this.actual = actual;
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
            
            actual.onSubscribe(s);
        }
        void open() {
            if (gate == 0) {
                UNSAFE.compareAndSwapInt(this, GATE, 0, 1); 
            }
        }
        @Override
        public void onNext(T item) {
            if (gate == 1) {
                actual.onNext(item);
            }
        }
        @Override
        public void onError(Throwable throwable) {
            if (UNSAFE.getAndSetInt(this, GATE, 2) != 2) {
                actual.onError(throwable);
            }
        }
        @Override
        public void onComplete() {
            if (UNSAFE.getAndSetInt(this, GATE, 2) != 2) {
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
