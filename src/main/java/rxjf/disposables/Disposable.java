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
package rxjf.disposables;

import java.util.concurrent.Future;

import rxjf.Flow.Subscription;
import rxjf.internal.disposables.BooleanDisposable;

/**
 * Represents a disposable resource.
 * <p>
 * Implementations MUST ensure both methods are thread-safe and {@code dispose()} is
 * idempotent.
 */
public interface Disposable {
    
    /**
     * Returns {@code true} if the resource is disposed.
     * @return {@code true} if the resource is disposed
     */
    boolean isDisposed();
    
    /**
     * Disposes the resource.
     * <p>
     * Implementors of this method should ensure idempotent operation.
     */
    void dispose();
    
    Disposable DISPOSED = new Disposable() {
        @Override
        public boolean isDisposed() {
            return true;
        }
        @Override
        public void dispose() {
        }
    };
    
    /**
     * Returns an already disposed Disposable.
     * @return an already disposed Disposable
     */
    static Disposable disposed() {
        return DISPOSED;
    }
    /**
     * Returns an new empty, undisposed disposable.
     * @return an new empty, undisposed disposable
     */
    static Disposable empty() {
        return new BooleanDisposable();
    }
    /**
     * Wraps the given Runnable instance into a disposable whose
     * dispose() method calls this runnable's run() method.
     * @param run the Runnable to wrap
     * @return the new disposable
     */
    static Disposable from(Runnable run) {
        return new BooleanDisposable(run);
    }
    /**
     * Wraps the given Future instance into a disposable whose
     * dispose() method calls the future's cancel() method with true.
     * @param future the Future to wrap
     * @return the new disposable
     */
    static Disposable from(Future<?> future) {
        return from(future, true);
    }
    /**
     * Wraps the given Future instance into a disposable whose
     * dispose() method calls the future's cancel() method with the specified interrupt flag.
     * @param future the Future to wrap
     * @param interruptOnCancel call the cancel() method with this parameter's value
     * @return the new disposable
     */
    static Disposable from(Future<?> future, boolean interruptOnCancel) {
        if (interruptOnCancel) {
            return from(() -> future.cancel(true));
        }
        return from(() -> future.cancel(false));
    }
    /**
     * Wraps the given Subscription instance into a disposable whose
     * dispose() method calls the subscription's cancel() method or
     * returns the instance if it is also a Disposable.
     * @param s the Subscription to wrap
     * @return the new disposable
     */
    static Disposable from(Subscription s) {
        if (s instanceof Disposable) {
            return (Disposable)s;
        }
        return from(s::cancel);
    }
}
