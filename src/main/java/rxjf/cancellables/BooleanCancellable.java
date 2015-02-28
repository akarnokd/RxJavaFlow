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

import rxjf.Flow.Subscription;

/**
 * 
 */
public final class BooleanCancellable implements Cancellable {
    static final Runnable STATE_CANCELLED = new Runnable() {
        @Override
        public void run() {
            
        }
    };
    volatile Runnable state;
    static final long STATE = addressOf(BooleanCancellable.class, "state");
    static final Runnable EMPTY = () -> { };
    public BooleanCancellable() {
        UNSAFE.putOrderedObject(this, STATE, EMPTY);
    }
    public BooleanCancellable(Runnable run) {
        Objects.requireNonNull(run);
        UNSAFE.putOrderedObject(this, STATE, run);
    }
    @Override
    public boolean isCancelled() {
        return state == STATE_CANCELLED;
    }
    @Override
    public void cancel() {
        if (state != STATE_CANCELLED) {
            Runnable r = (Runnable)UNSAFE.getAndSetObject(this, STATE, STATE_CANCELLED);
            r.run();
        }
    }
    public static Cancellable wrap(Subscription s) {
        if (s instanceof Cancellable) {
            return (Cancellable)s;
        }
        return new BooleanCancellable(s::cancel);
    }
}
