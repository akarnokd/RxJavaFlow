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
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.internal.schedulers.EventLoopsScheduler;
import rxjf.internal.subscriptions.AbstractSubscription;
import rxjf.schedulers.Scheduler;
import rxjf.subscribers.*;
import static rxjf.internal.UnsafeAccess.*;

/**
 * 
 */
public final class ScalarSynchronousFlow<T> extends Flowable<T> {
    final T value;
    private ScalarSynchronousFlow(OnSubscribe<T> onSubscribe, T value) {
        super(onSubscribe);
        this.value = value;
    }
    
    public static <T> ScalarSynchronousFlow<T> create(T value) {
        return new ScalarSynchronousFlow<>(
                s -> {
                    s.onSubscribe(new AbstractSubscription<T>(s) {
                        @Override
                        protected void onRequested(long n) {
                            s.onNext(value);
                            s.onComplete();
                        }
                    });
                },
                value);
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
            AbstractDisposableSubscriber<? super T> cs = DisposableSubscriber.wrap(child);
            cs.add(es.scheduleDirect(new ScalarSynchronousAction<>(cs, value)));
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
            AbstractDisposableSubscriber<? super T> cs = DisposableSubscriber.wrap(child);
            Scheduler.Worker worker = scheduler.createWorker();
            cs.add(worker);
            worker.schedule(new ScalarSynchronousAction<>(cs, value));
        }
    }
    /** Action that emits a single value when called. */
    static final class ScalarSynchronousAction<T> implements Runnable, Subscription {
        private final Subscriber<? super T> subscriber;
        private final T value;
        volatile int once;
        static final long ONCE = addressOf(ScalarSynchronousAction.class, "once");

        private ScalarSynchronousAction(Subscriber<? super T> subscriber,
                T value) {
            this.subscriber = subscriber;
            this.value = value;
        }

        @Override
        public void run() {
            subscriber.onSubscribe(this);
        }
        @Override
        public void request(long n) {
            if (UNSAFE.getAndSetInt(this, ONCE, 1) == 0) {
                try {
                    subscriber.onNext(value);
                } catch (Throwable t) {
                    subscriber.onError(t);
                    return;
                }
                subscriber.onComplete();
            }
        }
        @Override
        public void cancel() {
            UNSAFE.getAndSetInt(this, ONCE, 1);
        }
    }
}
