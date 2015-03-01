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
package rxjf.internal.operators;

import java.util.concurrent.*;

import rxjf.Flow.Subscriber;
import rxjf.*;
import rxjf.Flowable.Operator;
import rxjf.disposables.SerialDisposable;
import rxjf.schedulers.Scheduler;
import rxjf.subscribers.*;

/**
 * Applies a timeout policy for each element in the observable sequence, using
 * the specified scheduler to run timeout timers. If the next element isn't
 * received within the specified timeout duration starting from its predecessor,
 * the other observable sequence is used to produce future messages from that
 * point on.
 */
public final class OperatorTimeout<T> implements Operator<T, T> {

    final long timeout;
    final TimeUnit timeUnit;
    final Flowable<? extends T> other;
    final Scheduler scheduler;

    public OperatorTimeout(long timeout, TimeUnit timeUnit, 
            Flowable<? extends T> other, Scheduler scheduler) {
        this.timeout = timeout;
        this.timeUnit = timeUnit;
        this.other = other;
        this.scheduler = scheduler;
    }
    
    @Override
    public Subscriber<? super T> apply(Subscriber<? super T> child) {
        AbstractDisposableSubscriber<? super T> disposable = DisposableSubscriber.wrap(child);
        
        SerializedSubscriber<? super T> serialized = disposable.toSerialized();
        
        Scheduler.Worker worker = scheduler.createWorker();
        SerialDisposable timeouts = new SerialDisposable();

        disposable.add(worker);
        disposable.add(timeouts);
        
        return new AbstractSubscriber<T>() {
            @Override
            protected void onSubscribe() {
                serialized.onSubscribe(subscription);
                scheduleTimeout();
            }
            @Override
            public void onNext(T item) {
                serialized.onNext(item);
                scheduleTimeout();
            }
            @Override
            public void onError(Throwable throwable) {
                serialized.onError(throwable);
            }
            @Override
            public void onComplete() {
                serialized.onComplete();
            }
            
            void scheduleTimeout() {
                timeouts.set(worker.schedule(() -> {
                    if (other == null) {
                        serialized.onError(new TimeoutException());
                    } else {
                        subscribeOther();
                    }
                }, timeout, timeUnit));
            }
            void subscribeOther() {
                // cancel subscription
                // take unfulfilled requests
                // subscribe to other
                // request unfulfilled
                // TODO
            }
        };
    }
}
