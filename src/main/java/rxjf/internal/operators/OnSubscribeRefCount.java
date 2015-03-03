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

package rxjf.internal.operators;

import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import rxjf.*;
import rxjf.Flow.Subscriber;
import rxjf.Flowable.OnSubscribe;
import rxjf.disposables.*;
import rxjf.subscribers.*;

/**
 * Returns an observable sequence that stays connected to the source as long as
 * there is at least one subscription to the observable sequence.
 * 
 * @param <T>
 *            the value type
 */
public final class OnSubscribeRefCount<T> implements OnSubscribe<T> {

    private final ConnectableFlowable<? extends T> source;
    private volatile CompositeDisposable baseSubscription = new CompositeDisposable();
    private final AtomicInteger subscriptionCount = new AtomicInteger(0);

    /**
     * Use this lock for every subscription and disconnect action.
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Constructor.
     * 
     * @param source
     *            observable to apply ref count to
     */
    public OnSubscribeRefCount(ConnectableFlowable<? extends T> source) {
        this.source = source;
    }

    @Override
    public void accept(final Subscriber<? super T> s) {

        DisposableSubscriber<? super T> subscriber = DisposableSubscriber.from(s);
        
        lock.lock();
        if (subscriptionCount.incrementAndGet() == 1) {

            final AtomicBoolean writeLocked = new AtomicBoolean(true);

            try {
                // need to use this overload of connect to ensure that
                // baseSubscription is set in the case that source is a
                // synchronous Flowable
                source.connect(onSubscribe(subscriber, writeLocked));
            } finally {
                // need to cover the case where the source is subscribed to
                // outside of this class thus preventing the Action1 passed
                // to source.connect above being called
                if (writeLocked.get()) {
                    // Action1 passed to source.connect was not called
                    lock.unlock();
                }
            }
        } else {
            try {
                // ready to subscribe to source so do it
                doSubscribe(subscriber, baseSubscription);
            } finally {
                // release the read lock
                lock.unlock();
            }
        }

    }

    private Consumer<Disposable> onSubscribe(final DisposableSubscriber<? super T> subscriber,
            final AtomicBoolean writeLocked) {
        return d -> {
            try {
                baseSubscription.add(d);
                // ready to subscribe to source so do it
                doSubscribe(subscriber, baseSubscription);
            } finally {
                // release the write lock
                lock.unlock();
                writeLocked.set(false);
            }
        };
    }
    
    void doSubscribe(final DisposableSubscriber<? super T> subscriber, final CompositeDisposable currentBase) {
        // handle unsubscribing from the base subscription
        subscriber.add(disconnect(currentBase));
        
        source.unsafeSubscribe(new AbstractSubscriber<T>() {
            @Override
            protected void onSubscribe() {
                subscriber.onSubscribe(subscription);
            }
            @Override
            public void onError(Throwable e) {
                cleanup();
                subscriber.onError(e);
            }
            @Override
            public void onNext(T t) {
                subscriber.onNext(t);
            }
            @Override
            public void onComplete() {
                cleanup();
                subscriber.onComplete();
            }
            void cleanup() {
                // on error or completion we need to unsubscribe the base subscription
                // and set the subscriptionCount to 0 
                lock.lock();
                try {
                    if (baseSubscription == currentBase) {
                        baseSubscription.dispose();
                        baseSubscription = new CompositeDisposable();
                        subscriptionCount.set(0);
                    }
                } finally {
                    lock.unlock();
                }
            }
        });
    }

    private Disposable disconnect(final CompositeDisposable current) {
        return Disposable.from(() -> {
            lock.lock();
            try {
                if (baseSubscription == current) {
                    if (subscriptionCount.decrementAndGet() == 0) {
                        baseSubscription.dispose();
                        // need a new baseSubscription because once
                        // unsubscribed stays that way
                        baseSubscription = new CompositeDisposable();
                    }
                }
            } finally {
                lock.unlock();
            }
        });
    }
}