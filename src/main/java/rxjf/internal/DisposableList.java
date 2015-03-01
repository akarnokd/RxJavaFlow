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
package rxjf.internal;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import rxjf.disposables.Disposable;
import rxjf.exceptions.Exceptions;

/**
 * Disposable that represents a group of Disposables that are unsubscribed together.
 * 
 * @see <a href="http://msdn.microsoft.com/en-us/library/system.reactive.disposables.compositedisposable(v=vs.103).aspx">Rx.Net equivalent CompositeDisposable</a>
 */
public final class DisposableList implements Disposable {

    private LinkedList<Disposable> disposables;
    private volatile boolean unsubscribed;
    private final ReentrantLock lock = new ReentrantLock();

    public DisposableList() {
    }

    public DisposableList(final Disposable... disposables) {
        this.disposables = new LinkedList<>(Arrays.asList(disposables));
    }

    public DisposableList(Disposable s) {
        this.disposables = new LinkedList<>();
        this.disposables.add(s);
    }

    @Override
    public boolean isDisposed() {
        return unsubscribed;
    }

    /**
     * Adds a new {@link Disposable} to this {@code DisposableList} if the {@code DisposableList} is
     * not yet unsubscribed. If the {@code DisposableList} <em>is</em> unsubscribed, {@code add} will
     * indicate this by explicitly unsubscribing the new {@code Disposable} as well.
     *
     * @param s
     *          the {@link Disposable} to add
     */
    public void add(final Disposable s) {
        if (s.isDisposed()) {
            return;
        }
        if (!unsubscribed) {
            lock.lock();
            try {
                if (!unsubscribed) {
                    LinkedList<Disposable> subs = disposables;
                    if (subs == null) {
                        subs = new LinkedList<>();
                        disposables = subs;
                    }
                    subs.add(s);
                    return;
                }
            } finally {
                lock.unlock();
            }
        }
        // call after leaving the synchronized block so we're not holding a lock while executing this
        s.dispose();
    }

    public void remove(final Disposable s) {
        if (!unsubscribed) {
            lock.lock();
            try {
                LinkedList<Disposable> subs = disposables;
                if (unsubscribed || subs == null || !subs.remove(s)) {
                    return;
                }
            } finally {
                lock.unlock();
            }
            // if we removed successfully we then need to call unsubscribe on it (outside of the lock)
            s.dispose();
        }
    }

    /**
     * Unsubscribe from all of the disposables in the list, which stops the receipt of notifications on
     * the associated {@code Subscriber}.
     */
    @Override
    public void dispose() {
        if (!unsubscribed) {
            List<Disposable> list;
            lock.lock();
            try {
                if (unsubscribed) {
                    return;
                }
                unsubscribed = true;
                list = disposables;
                disposables = null;
            } finally {
                lock.unlock();
            }
            // we will only get here once
            unsubscribeFromAll(list);
        }
    }

    private static void unsubscribeFromAll(Collection<Disposable> disposables) {
        if (disposables == null) {
            return;
        }
        List<Throwable> es = null;
        for (Disposable s : disposables) {
            try {
                s.dispose();
            } catch (Throwable e) {
                if (es == null) {
                    es = new ArrayList<>();
                }
                es.add(e);
            }
        }
        Exceptions.throwIfAny(es);
    }
    /* perf support */
    public void clear() {
        if (!unsubscribed) {
            List<Disposable> list;
            lock.lock();
            try {
                list = disposables;
                disposables = null;
            } finally {
                lock.unlock();
            }
            unsubscribeFromAll(list);
        }
    }
    /**
     * Returns true if this composite is not unsubscribed and contains disposables.
     * @return {@code true} if this composite is not unsubscribed and contains disposables.
     */
    public boolean hasDisposables() {
        if (!unsubscribed) {
            lock.lock();
            try {
                return !unsubscribed && disposables != null && !disposables.isEmpty();
            } finally {
                lock.unlock();
            }
        }
        return false;
    }
}
