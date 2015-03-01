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
    
    static Disposable disposed() {
        return DISPOSED;
    }
    static Disposable empty() {
        return new BooleanDisposable();
    }
    static Disposable from(Runnable run) {
        return new BooleanDisposable(run);
    }
    static Disposable from(Future<?> future) {
        return from(future, true);
    }
    static Disposable from(Future<?> future, boolean interruptOnCancel) {
        if (interruptOnCancel) {
            return from(() -> future.cancel(true));
        }
        return from(() -> future.cancel(false));
    }
    static Disposable from(Subscription s) {
        if (s instanceof Disposable) {
            return (Disposable)s;
        }
        return from(s::cancel);
    }
}
