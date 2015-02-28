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
package rxjf.internal.schedulers;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import rxjf.cancellables.*;
import rxjf.exceptions.Exceptions;
import rxjf.plugins.*;
import rxjf.schedulers.Scheduler;

/**
 * TODO class description missing
 */
public class NewThreadWorker implements Scheduler.Worker {
    private final ScheduledExecutorService executor;
    private final RxJavaFlowSchedulersHook schedulersHook;
    volatile boolean isUnsubscribed;
    /** The purge frequency in milliseconds. */
    private static final String FREQUENCY_KEY = "rx.scheduler.jdk6.purge-frequency-millis";
    /** Force the use of purge (true/false). */
    private static final String PURGE_FORCE_KEY = "rx.scheduler.jdk6.purge-force";
    private static final String PURGE_THREAD_PREFIX = "RxSchedulerPurge-";
    /** Forces the use of purge even if setRemoveOnCancelPolicy is available. */
    private static final boolean PURGE_FORCE;
    /** The purge frequency in milliseconds. */
    public static final int PURGE_FREQUENCY;
    private static final ConcurrentHashMap<ScheduledThreadPoolExecutor, ScheduledThreadPoolExecutor> EXECUTORS;
    private static final AtomicReference<ScheduledExecutorService> PURGE;
    static {
        EXECUTORS = new ConcurrentHashMap<>();
        PURGE = new AtomicReference<>();
        PURGE_FORCE = Boolean.getBoolean(PURGE_FORCE_KEY);
        PURGE_FREQUENCY = Integer.getInteger(FREQUENCY_KEY, 1000);
    }
    /** 
     * Registers the given executor service and starts the purge thread if not already started. 
     * <p>{@code public} visibility reason: called from other package(s) within RxJava
     * @param service a scheduled thread pool executor instance 
     */
    public static void registerExecutor(ScheduledThreadPoolExecutor service) {
        do {
            ScheduledExecutorService exec = PURGE.get();
            if (exec != null) {
                break;
            }
            exec = Executors.newScheduledThreadPool(1, new RxThreadFactory(PURGE_THREAD_PREFIX));
            if (PURGE.compareAndSet(null, exec)) {
                exec.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        purgeExecutors();
                    }
                }, PURGE_FREQUENCY, PURGE_FREQUENCY, TimeUnit.MILLISECONDS);
                
                break;
            }
        } while (true);
        
        EXECUTORS.putIfAbsent(service, service);
    }
    /** 
     * Deregisters the executor service. 
     * <p>{@code public} visibility reason: called from other package(s) within RxJava
     * @param service a scheduled thread pool executor instance 
     */
    public static void deregisterExecutor(ScheduledExecutorService service) {
        EXECUTORS.remove(service);
    }
    /** Purges each registered executor and eagerly evicts shutdown executors. */
    static void purgeExecutors() {
        try {
            Iterator<ScheduledThreadPoolExecutor> it = EXECUTORS.keySet().iterator();
            while (it.hasNext()) {
                ScheduledThreadPoolExecutor exec = it.next();
                if (!exec.isShutdown()) {
                    exec.purge();
                } else {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            Exceptions.throwIfFatal(t);
            RxJavaFlowPlugins.getInstance().getErrorHandler().handleError(t);
        }
    }
    
    /** 
     * Tries to enable the Java 7+ setRemoveOnCancelPolicy.
     * <p>{@code public} visibility reason: called from other package(s) within RxJava.
     * If the method returns false, the {@link #registerExecutor(ScheduledThreadPoolExecutor)} may
     * be called to enable the backup option of purging the executors.
     * @param exec the executor to call setRemoveOnCaneclPolicy if available.
     * @return true if the policy was successfully enabled 
     */
    public static boolean tryEnableCancelPolicy(ScheduledExecutorService exec) {
        if (!PURGE_FORCE) {
            for (Method m : exec.getClass().getMethods()) {
                if (m.getName().equals("setRemoveOnCancelPolicy")
                        && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0] == Boolean.TYPE) {
                    try {
                        m.invoke(exec, true);
                        return true;
                    } catch (Exception ex) {
                        RxJavaFlowPlugins.getInstance().getErrorHandler().handleError(ex);
                    }
                }
            }
        }
        return false;
    }
    
    /* package */
    public NewThreadWorker(ThreadFactory threadFactory) {
        ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, threadFactory);
        // Java 7+: cancelled future tasks can be removed from the executor thus avoiding memory leak
        boolean cancelSupported = tryEnableCancelPolicy(exec);
        if (!cancelSupported && exec instanceof ScheduledThreadPoolExecutor) {
            registerExecutor((ScheduledThreadPoolExecutor)exec);
        }
        schedulersHook = RxJavaFlowPlugins.getInstance().getSchedulersHook();
        executor = exec;
    }

    @Override
    public Cancellable schedule(final Runnable action) {
        return schedule(action, 0, null);
    }

    @Override
    public Cancellable schedule(final Runnable action, long delayTime, TimeUnit unit) {
        if (isUnsubscribed) {
            return Cancellable.CANCELLED;
        }
        return scheduleActual(action, delayTime, unit);
    }

    /**
     * TODO javadoc missing
     * @param action
     * @param delayTime
     * @param unit
     * @return
     */
    public ScheduledRunnable scheduleActual(final Runnable action, long delayTime, TimeUnit unit) {
        Runnable decoratedAction = schedulersHook.onSchedule(action);
        ScheduledRunnable run = new ScheduledRunnable(decoratedAction);
        Future<?> f;
        if (delayTime <= 0) {
            f = executor.submit(run);
        } else {
            f = executor.schedule(run, delayTime, unit);
        }
        run.add(f);

        return run;
    }
    public ScheduledRunnable scheduleActual(final Runnable action, long delayTime, TimeUnit unit, CompositeCancellable parent) {
        Runnable decoratedAction = schedulersHook.onSchedule(action);
        ScheduledRunnable run = new ScheduledRunnable(decoratedAction, parent);
        parent.add(run);

        Future<?> f;
        if (delayTime <= 0) {
            f = executor.submit(run);
        } else {
            f = executor.schedule(run, delayTime, unit);
        }
        run.add(f);

        return run;
    }
    
    @Override
    public void cancel() {
        isUnsubscribed = true;
        executor.shutdownNow();
        deregisterExecutor(executor);
    }

    @Override
    public boolean isCancelled() {
        return isUnsubscribed;
    }
}
