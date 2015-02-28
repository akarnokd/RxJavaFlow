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

package rxjf.cancellables;

import static rxjf.internal.UnsafeAccess.*;

import java.util.Objects;

/**
 * 
 */
public final class MultipleAssignmentCancellable implements Cancellable {
    /** Unique terminal state. */
    static final Cancellable STATE_CANCELLED = new Cancellable() {
        @Override
        public void cancel() {
            
        }
        @Override
        public boolean isCancelled() {
            return true;
        }
    };
    volatile Cancellable state;
    static final long STATE = addressOf(MultipleAssignmentCancellable.class, "state");
    public MultipleAssignmentCancellable() {
        
    }
    public MultipleAssignmentCancellable(Cancellable cancellable) {
        Objects.requireNonNull(cancellable);
        UNSAFE.putOrderedObject(this, STATE, cancellable);
    }
    public void set(Cancellable cancellable) {
        Objects.requireNonNull(cancellable);
        for (;;) {
            Cancellable c = state;
            if (c == STATE_CANCELLED) {
                return;
            }
            if (UNSAFE.compareAndSwapObject(this, STATE, c, cancellable)) {
                return;
            }
        }
    }
    @Override
    public void cancel() {
        if (state != STATE_CANCELLED) {
            Cancellable c = (Cancellable)UNSAFE.getAndSetObject(this, STATE, STATE_CANCELLED);
            if (c != null) {
                c.cancel();
            }
        }
    }
    @Override
    public boolean isCancelled() {
        return state == STATE_CANCELLED;
    }
}
