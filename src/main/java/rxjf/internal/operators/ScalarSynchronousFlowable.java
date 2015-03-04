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

import static rxjf.internal.UnsafeAccess.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.*;
import rxjf.disposables.Disposable;
import rxjf.exceptions.Exceptions;
import rxjf.internal.*;
import rxjf.internal.schedulers.EventLoopsScheduler;
import rxjf.internal.subscriptions.ScalarSubscription;
import rxjf.schedulers.Scheduler;

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
            DirectScheduledRequest<T> sr = new DirectScheduledRequest<>(child, value, es);
            try {
                child.onSubscribe(sr);
            } catch (Throwable t) {
                throw Conformance.onSubscribeThrew(t);
            }
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
            NormalScheduledRequest<T> sr = new NormalScheduledRequest<>(child, value, scheduler);
            try {
                child.onSubscribe(sr);
            } catch (Throwable t) {
                throw Conformance.onSubscribeThrew(t);
            }
        }
    }
    
    /**
     * Base class for scheduling an scalar emission in a cancellable way.
     *
     * @param <T> the emitted value type
     */
    static abstract class AbstractScheduledRequest<T> implements Subscription, Runnable {
        final Subscriber<? super T> child;
        final T value;
        
        volatile int once;
        static final long ONCE = addressOf(DirectScheduledRequest.class, "once");
        
        volatile Disposable task;
        static final long TASK = addressOf(DirectScheduledRequest.class, "task");

        public AbstractScheduledRequest(Subscriber<? super T> child, T value) {
            this.child = child;
            this.value = value;
        }
        
        protected abstract Disposable schedule();
        @Override
        public void request(long n) {
            if (UNSAFE.getAndSetInt(this, ONCE, 1) == 0) {
                Disposable t = schedule();
                TerminalAtomics.set(this, TASK, t);
            }
        }
        @Override
        public void run() {
            try {
                try {
                    child.onNext(value);
                } catch (Throwable t) {
                    Throwable t1 = Conformance.onNextThrew(t);
                    try {
                        child.onError(t1);
                    } catch (Throwable t2) {
                        Exceptions.handleUncaught(t1);
                        Exceptions.handleUncaught(Conformance.onErrorThrew(t2));
                    }
                    return;
                }
                try {
                    child.onComplete();
                } catch (Throwable t3) {
                    Exceptions.handleUncaught(Conformance.onCompleteThrew(t3));
                }
            } finally {
                TerminalAtomics.disposeSilently(this, TASK);
            }
        }
        @Override
        public void cancel() {
            UNSAFE.getAndSetInt(this, ONCE, 1);
            TerminalAtomics.dispose(this, TASK);
        }
    }
    /**
     * Schedules itself on the EventLoopsScheduler once the child requests any value.
     *
     * @param <T> the emitted value type
     */
    static final class DirectScheduledRequest<T> extends AbstractScheduledRequest<T> {
        final EventLoopsScheduler scheduler;
        
        public DirectScheduledRequest(Subscriber<? super T> child, T value, EventLoopsScheduler scheduler) {
            super(child, value);
            this.scheduler = scheduler;
        }
        @Override
        protected Disposable schedule() {
            return scheduler.scheduleDirect(this);
        }
    }
    /**
     * Schedules itself on a general scheduler once the child requests any value.
     *
     * @param <T>
     */
    static final class NormalScheduledRequest<T> extends AbstractScheduledRequest<T> {
        final Scheduler scheduler;
        
        public NormalScheduledRequest(Subscriber<? super T> child, T value, Scheduler scheduler) {
            super(child, value);
            this.scheduler = scheduler;
        }
        @Override
        protected Disposable schedule() {
            Scheduler.Worker w = scheduler.createWorker();
            w.schedule(this);
            return w;
        }
    }
}
