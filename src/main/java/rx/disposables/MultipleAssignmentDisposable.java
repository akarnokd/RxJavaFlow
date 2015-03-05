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

package rx.disposables;

import static rx.internal.UnsafeAccess.*;

import java.util.Objects;

/**
 * 
 */
public final class MultipleAssignmentDisposable implements Disposable {
    /** Unique terminal state. */
    static final Disposable STATE_CANCELLED = new Disposable() {
        @Override
        public void dispose() {
            
        }
        @Override
        public boolean isDisposed() {
            return true;
        }
    };
    volatile Disposable state;
    static final long STATE = addressOf(MultipleAssignmentDisposable.class, "state");
    public MultipleAssignmentDisposable() {
        
    }
    public MultipleAssignmentDisposable(Disposable disposable) {
        Objects.requireNonNull(disposable);
        UNSAFE.putOrderedObject(this, STATE, disposable);
    }
    public Disposable get() {
        Disposable d = state;
        if (d == STATE_CANCELLED) {
            return Disposable.DISPOSED;
        }
        return d;
    }
    public void set(Disposable disposable) {
        Objects.requireNonNull(disposable);
        for (;;) {
            Disposable c = state;
            if (c == STATE_CANCELLED) {
                disposable.dispose();
                return;
            }
            if (UNSAFE.compareAndSwapObject(this, STATE, c, disposable)) {
                return;
            }
        }
    }
    @Override
    public void dispose() {
        if (state != STATE_CANCELLED) {
            Disposable c = (Disposable)UNSAFE.getAndSetObject(this, STATE, STATE_CANCELLED);
            if (c != null) {
                c.dispose();
            }
        }
    }
    @Override
    public boolean isDisposed() {
        return state == STATE_CANCELLED;
    }
}
