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

package rxjf.internal.disposables;

import static rxjf.internal.UnsafeAccess.*;

import java.util.Objects;

import rxjf.disposables.Disposable;

/**
 * 
 */
public final class BooleanDisposable implements Disposable {
    static final Runnable STATE_CANCELLED = () -> { };
    volatile Runnable state;
    static final long STATE = addressOf(BooleanDisposable.class, "state");
    static final Runnable EMPTY = () -> { };
    public BooleanDisposable() {
        UNSAFE.putOrderedObject(this, STATE, EMPTY);
    }
    public BooleanDisposable(Runnable run) {
        Objects.requireNonNull(run);
        UNSAFE.putOrderedObject(this, STATE, run);
    }
    @Override
    public boolean isDisposed() {
        return state == STATE_CANCELLED;
    }
    @Override
    public void dispose() {
        if (state != STATE_CANCELLED) {
            Runnable r = (Runnable)UNSAFE.getAndSetObject(this, STATE, STATE_CANCELLED);
            r.run();
        }
    }
}
