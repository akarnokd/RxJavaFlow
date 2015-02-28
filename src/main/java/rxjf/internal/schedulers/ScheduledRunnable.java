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

package rxjf.internal.schedulers;

import static rxjf.internal.UnsafeAccess.UNSAFE;

import java.util.concurrent.Future;

import rxjf.cancellables.*;
import rxjf.internal.UnsafeAccess;

public final class ScheduledRunnable implements Runnable, Cancellable {
    final Runnable actual;
    final CompositeCancellable composite;
    volatile Object runner;
    static final Object DONE = new Object();
    static final long RUNNER = UnsafeAccess.addressOf(ScheduledRunnable.class, "runner");
    
    public ScheduledRunnable(Runnable actual) {
        this.actual = actual;
        this.composite = new CompositeCancellable();
    }
    public ScheduledRunnable(Runnable actual, CompositeCancellable parent) {
        this.actual = actual;
        this.composite = new CompositeCancellable(new BooleanCancellable(() -> {
            parent.remove(ScheduledRunnable.this);
        }));
    }
    @Override
    public void run() {
        UNSAFE.putOrderedObject(this, RUNNER, Thread.currentThread());
        try {
            actual.run();
        } catch (Throwable e) {
            Thread t = Thread.currentThread();
            t.getUncaughtExceptionHandler().uncaughtException(t, e);
        } finally {
            UNSAFE.putOrderedObject(this, RUNNER, DONE);
            cancel();
        }
    }
    public void add(Cancellable cancellable) {
        composite.add(cancellable);
    }
    public void add(Future<?> future) {
        add(new BooleanCancellable(() -> {
            Object o = runner;
            if (o != DONE) {
                if (o == Thread.currentThread()) {
                    future.cancel(false);
                } else {
                    future.cancel(true);
                }
            }
        }));
    }
    public void addParent(CompositeCancellable parent) {
        add(new BooleanCancellable(() -> {
            parent.remove(ScheduledRunnable.this);
        }));
    }
    @Override
    public boolean isCancelled() {
        return composite.isCancelled();
    }
    @Override
    public void cancel() {
        composite.cancel();
    }
}