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
package rx.internal.schedulers;

import static rx.internal.UnsafeAccess.*;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import rx.disposables.*;
import rx.exceptions.Exceptions;
import rx.internal.disposables.BooleanDisposable;
import rx.internal.queues.MpscLinkedQueue;
import rx.plugins.RxJavaPlugins;
import rx.schedulers.Scheduler;

/**
 * Scheduler that wraps an Executor instance and establishes the Scheduler contract upon it.
 * <p>
 * Note that thread-hopping is unavoidable with this kind of Scheduler as we don't know about the underlying
 * threading behavior of the executor.
 */
public final class ExecutorScheduler implements Scheduler {
    final Executor executor;
    public ExecutorScheduler(Executor executor) {
        this.executor = executor;
    }

    @Override
    public Worker createWorker() {
        return new ExecutorSchedulerWorker(executor);
    }

    /** Worker that schedules tasks on the executor indirectly through a trampoline mechanism. */
    static final class ExecutorSchedulerWorker implements Scheduler.Worker, Runnable {
        final Executor executor;
        // TODO: use a better performing structure for task tracking
        final CompositeDisposable tasks;
        final MpscLinkedQueue<ExecutorRunnable> queue; 
        final AtomicInteger wip;
        
        public ExecutorSchedulerWorker(Executor executor) {
            this.executor = executor;
            this.queue = new MpscLinkedQueue<>();
            this.wip = new AtomicInteger();
            this.tasks = new CompositeDisposable();
        }

        @Override
        public Disposable schedule(Runnable action) {
            if (isDisposed()) {
                return Disposable.DISPOSED;
            }
            ExecutorRunnable ea = new ExecutorRunnable(action, tasks);
            tasks.add(ea);
            queue.offer(ea);
            if (wip.getAndIncrement() == 0) {
                try {
                    executor.execute(this);
                } catch (RejectedExecutionException t) {
                    // cleanup if rejected
                    tasks.remove(ea);
                    wip.decrementAndGet();
                    // report the error to the plugin
                    RxJavaPlugins.getInstance().getErrorHandler().handleError(t);
                    // throw it to the caller
                    throw t;
                }
            }
            
            return ea;
        }

        @Override
        public void run() {
            do {
                queue.poll().run();
            } while (wip.decrementAndGet() > 0);
        }
        
        @Override
        public Disposable schedule(final Runnable action, long delayTime, TimeUnit unit) {
            if (delayTime <= 0) {
                return schedule(action);
            }
            if (isDisposed()) {
                return Disposable.DISPOSED;
            }
            ScheduledExecutorService service;
            if (executor instanceof ScheduledExecutorService) {
                service = (ScheduledExecutorService)executor;
            } else {
                service = GenericScheduledExecutorService.getInstance();
            }
            
            final MultipleAssignmentDisposable mas = new MultipleAssignmentDisposable();
            // tasks.add(mas); // Needs a removal without unsubscription
            MultipleAssignmentDisposable first = new MultipleAssignmentDisposable();
            mas.set(first);
            
            try {
                Future<?> f = service.schedule(() -> {
                    if (mas.isDisposed()) {
                        return;
                    }
                    mas.set(schedule(action));
                }, delayTime, unit);
                
                first.set(new BooleanDisposable(() -> f.cancel(true)));
            } catch (RejectedExecutionException t) {
                // report the rejection to plugins
                RxJavaPlugins.getInstance().getErrorHandler().handleError(t);
                throw t;
            }
            
            return mas;
        }

        @Override
        public boolean isDisposed() {
            return tasks.isDisposed();
        }

        @Override
        public void dispose() {
            tasks.dispose();
        }
        
    }

    /** Runs the actual action and maintains an unsubscription state. */
    static final class ExecutorRunnable implements Runnable, Disposable {
        final Runnable actual;
        final CompositeDisposable parent;
        volatile int cancelled;
        static final long CANCELLED = addressOf(ExecutorRunnable.class, "cancelled");

        public ExecutorRunnable(Runnable actual, CompositeDisposable parent) {
            this.actual = actual;
            this.parent = parent;
        }

        @Override
        public void run() {
            if (isDisposed()) {
                return;
            }
            try {
                actual.run();
            } catch (Throwable t) {
                Exceptions.handleUncaught(t);
                RxJavaPlugins.getInstance().getErrorHandler().handleError(t);
            } finally {
                dispose();
            }
        }
        @Override
        public boolean isDisposed() {
            return cancelled != 0;
        }

        @Override
        public void dispose() {
            if (UNSAFE.getAndSetInt(this, CANCELLED, 1) == 0) {
                parent.remove(this);
            }
        }
        
    }
}
