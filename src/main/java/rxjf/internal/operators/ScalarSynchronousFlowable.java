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

import rxjf.Flow.Subscriber;
import rxjf.*;
import rxjf.internal.schedulers.EventLoopsScheduler;
import rxjf.internal.subscriptions.*;
import rxjf.schedulers.Scheduler;
import rxjf.subscribers.DisposableSubscriber;

/**
 * 
 */
public final class ScalarSynchronousFlowable<T> extends Flowable<T> {
    final T value;
    private ScalarSynchronousFlowable(OnSubscribe<T> onSubscribe, T value) {
        super(onSubscribe);
        this.value = value;
    }
    
    public static <T> ScalarSynchronousFlowable<T> create(T value) {
        return new ScalarSynchronousFlowable<>(s -> s.onSubscribe(new ScalarSubscription<>(s, value)), value);
    }
    
    public T get() {
        return value;
    }
    /**
     * Customized observeOn/subscribeOn implementation which emits the scalar
     * value directly or with less overhead on the specified scheduler.
     * @param scheduler the target scheduler
     * @return the new observable
     */
    public Flowable<T> scalarScheduleOn(Scheduler scheduler) {
        if (scheduler instanceof EventLoopsScheduler) {
            EventLoopsScheduler es = (EventLoopsScheduler) scheduler;
            return create(new DirectScheduledEmission<>(es, value));
        }
        return create(new NormalScheduledEmission<>(scheduler, value));
    }
    
    /** Optimized observeOn for scalar value observed on the EventLoopsScheduler. */
    static final class DirectScheduledEmission<T> implements OnSubscribe<T> {
        private final EventLoopsScheduler es;
        private final T value;
        DirectScheduledEmission(EventLoopsScheduler es, T value) {
            this.es = es;
            this.value = value;
        }
        @Override
        public void accept(final Subscriber<? super T> child) {
            DisposableSubscriber<? super T> cs = DisposableSubscriber.from(child);
            cs.add(es.scheduleDirect(new ScalarSubscription<>(cs, value)));
        }
    }
    /** Emits a scalar value on a general scheduler. */
    static final class NormalScheduledEmission<T> implements OnSubscribe<T> {
        private final Scheduler scheduler;
        private final T value;

        NormalScheduledEmission(Scheduler scheduler, T value) {
            this.scheduler = scheduler;
            this.value = value;
        }
        
        @Override
        public void accept(final Subscriber<? super T> child) {
            DisposableSubscriber<? super T> cs = DisposableSubscriber.from(child);
            Scheduler.Worker worker = scheduler.createWorker();
            cs.add(worker);
            worker.schedule(new ScalarSubscription<>(cs, value));
        }
    }
}
