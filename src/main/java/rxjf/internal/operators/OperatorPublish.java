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

import java.util.function.*;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.disposables.Disposable;
import rxjf.internal.*;
import rxjf.internal.queues.SpscArrayQueue;
import rxjf.internal.subscriptions.AbstractSubscription;
import rxjf.subscribers.*;

public final class OperatorPublish<T> extends ConnectableFlowable<T> {
    final Flowable<? extends T> source;

    public static <T> ConnectableFlowable<T> create(Flowable<? extends T> source) {
        return new OperatorPublish<>(source);
    }

    public static <T, R> Flowable<R> create(final Flowable<? extends T> source, 
            final Function<? super Flowable<T>, ? extends Flowable<R>> selector) {
        return Flowable.create(child -> {
            DisposableSubscriber<R> ds = new DefaultDisposableSubscriber<>(child);
            OperatorPublish<T> op = new OperatorPublish<>(source);
            
            selector.apply(op).unsafeSubscribe(ds);
            
            op.connect(sub -> ds.add(sub));
        });
    }

    private OperatorPublish(Flowable<? extends T> source) {
        super(s -> {
            // TODO
        });
        this.source = source;
    }

    @Override
    public void connect(Consumer<? super Disposable> connection) {
        // TODO
    }
    
    /**
     * Manages an array of inner subscriptions for a PublishSubscriber.
     *
     * @param <T> the published value type
     */
    static final class PublishSubscriptionManager<T> extends AbstractArrayManager<InnerSubscription<T>> {
        @SuppressWarnings("unchecked")
        public PublishSubscriptionManager() {
            super(i -> new InnerSubscription[i]);
        }
    }
    
    static final class PublishSubscriber<T> extends AbstractSubscriber<T> {
        final NotificationLite<T> nl;
        final SpscArrayQueue<T> queue;
        Object terminal;
        volatile boolean done;
        
        /** Guarded by this. */
        boolean emitting;
        /** Guarded by this. */
        boolean missed;
        
        public PublishSubscriber() {
            this.nl = NotificationLite.instance();
            this.queue = new SpscArrayQueue<>(Flow.defaultBufferSize());
        }
        @Override
        protected void onSubscribe() {
            subscription.request(Flow.defaultBufferSize());
        }
        @Override
        public void onNext(T item) {
            Conformance.itemNonNull(item);
            Conformance.subscriptionNonNull(subscription);
            
            if (!queue.offer(item)) {
                terminal = nl.error(Conformance.mustRequestFirst());
                done = true;
            }
            dispatch();
        }
        @Override
        public void onError(Throwable throwable) {
            Conformance.throwableNonNull(throwable);
            Conformance.subscriptionNonNull(subscription);
            
            if (done) {
                return;
            }
            terminal = nl.error(throwable);
            done = true;
            dispatch();
        }
        @Override
        public void onComplete() {
            Conformance.subscriptionNonNull(subscription);
            
            if (done) {
                return;
            }
            terminal = nl.complete();
            done = true;
            dispatch();
        }
        
        void dispatch() {
            // TODO
        }
    }
    
    static final class InnerSubscription<T> implements Subscription {
        /** The current requested count, negative value indicates cancelled subscription. */
        volatile long requested;
        static final long REQUESTED = addressOf(AbstractSubscription.class, "requested");
        
        final Subscriber<? super T> subscriber;
        final PublishSubscriber<T> parent;
        
        public InnerSubscription(Subscriber<? super T> subscriber, PublishSubscriber<T> parent) {
            this.subscriber = Conformance.subscriberNonNull(subscriber);
            this.parent = parent;
        }
        
        @Override
        public void request(long n) {
            if (!Conformance.requestPositive(n, subscriber)) {
                cancel();
                return;
            }
        }
        
        @Override
        public void cancel() {
            // TODO Auto-generated method stub
            
        }
    }
}
