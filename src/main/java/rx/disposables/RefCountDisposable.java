/**
 * Copyright 2014 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package rx.disposables;

import static rx.internal.UnsafeAccess.*;

/**
 * Keeps track of the sub-subscriptions and unsubscribes the underlying subscription once all sub-subscriptions
 * have unsubscribed.
 */
public final class RefCountDisposable implements Disposable {
    private final Disposable actual;
    static final State EMPTY_STATE = new State(false, 0);
    volatile State state;
    static final long STATE = addressOf(RefCountDisposable.class, "state");

    private static final class State {
        final boolean isDisposed;
        final int children;

        State(boolean u, int c) {
            this.isDisposed = u;
            this.children = c;
        }

        State addChild() {
            return new State(isDisposed, children + 1);
        }

        State removeChild() {
            return new State(isDisposed, children - 1);
        }

        State unsubscribe() {
            return new State(true, children);
        }

    }

    /**
     * Creates a {@code RefCountSubscription} by wrapping the given non-null {@code Subscription}.
     * 
     * @param s
     *          the {@link Subscription} to wrap
     * @throws IllegalArgumentException
     *          if {@code s} is {@code null}
     */
    public RefCountDisposable(Disposable s) {
        if (s == null) {
            throw new IllegalArgumentException("s");
        }
        this.actual = s;
        UNSAFE.putOrderedObject(this, STATE, EMPTY_STATE);
    }

    /**
     * Returns a new sub-subscription
     *
     * @return a new sub-subscription.
     */
    public Disposable get() {
        State oldState;
        State newState;
        do {
            oldState = state;
            if (oldState.isDisposed) {
                return Disposable.DISPOSED;
            } else {
                newState = oldState.addChild();
            }
        } while (!UNSAFE.compareAndSwapObject(this, STATE, oldState, newState));

        return new InnerDisposable(this);
    }

    @Override
    public boolean isDisposed() {
        return state.isDisposed;
    }

    @Override
    public void dispose() {
        State oldState;
        State newState;
        do {
            oldState = state;
            if (oldState.isDisposed) {
                return;
            }
            newState = oldState.unsubscribe();
        } while (!UNSAFE.compareAndSwapObject(this, STATE, oldState, newState));
        
        unsubscribeActualIfApplicable(newState);
    }

    private void unsubscribeActualIfApplicable(State state) {
        if (state.isDisposed && state.children == 0) {
            actual.dispose();
        }
    }
    void unsubscribeAChild() {
        State oldState;
        State newState;
        do {
            oldState = state;
            newState = oldState.removeChild();
        } while (!UNSAFE.compareAndSwapObject(this, STATE, oldState, newState));
        unsubscribeActualIfApplicable(newState);
    }

    /** The individual sub-subscriptions. */
    private static final class InnerDisposable implements Disposable {
        final RefCountDisposable parent;
        volatile int innerDone;
        static final long INNER_DONE = addressOf(InnerDisposable.class, "innerDone");
        
        public InnerDisposable(RefCountDisposable parent) {
            this.parent = parent;
        }
        @Override
        public void dispose() {
            if (UNSAFE.compareAndSwapInt(this, INNER_DONE, 0, 1)) {
                parent.unsubscribeAChild();
            }
        }

        @Override
        public boolean isDisposed() {
            return innerDone != 0;
        }
    };
}
