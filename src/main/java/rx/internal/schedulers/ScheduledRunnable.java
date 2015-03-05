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

package rx.internal.schedulers;

import static rx.internal.UnsafeAccess.*;

import java.util.concurrent.Future;

import rx.disposables.*;
import rx.internal.disposables.BooleanDisposable;

public final class ScheduledRunnable implements Runnable, Disposable {
    final Runnable actual;
    final CompositeDisposable composite;
    volatile Object runner;
    static final Object DONE = new Object();
    static final long RUNNER = addressOf(ScheduledRunnable.class, "runner");
    
    public ScheduledRunnable(Runnable actual) {
        this.actual = actual;
        this.composite = new CompositeDisposable();
    }
    public ScheduledRunnable(Runnable actual, CompositeDisposable parent) {
        this.actual = actual;
        this.composite = new CompositeDisposable(new BooleanDisposable(() -> {
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
            dispose();
        }
    }
    public void add(Disposable disposable) {
        composite.add(disposable);
    }
    public void add(Future<?> future) {
        add(new BooleanDisposable(() -> {
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
    public void addParent(CompositeDisposable parent) {
        add(new BooleanDisposable(() -> {
            parent.remove(ScheduledRunnable.this);
        }));
    }
    @Override
    public boolean isDisposed() {
        return composite.isDisposed();
    }
    @Override
    public void dispose() {
        composite.dispose();
    }
}