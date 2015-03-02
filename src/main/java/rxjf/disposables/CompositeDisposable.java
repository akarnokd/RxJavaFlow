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

import java.util.*;

/**
 * Disposable that represents a group of Disposables that are disposed together.
 */
public final class CompositeDisposable implements Disposable {
    volatile boolean disposed;
    /** Guarded by this. */
    Set<Disposable> disposables;
    
    // TODO javadoc
    public CompositeDisposable() {
        
    }
    
    // TODO javadoc
    @SafeVarargs
    public CompositeDisposable(Disposable... disposables) {
        Objects.requireNonNull(disposables);
        this.disposables = new HashSet<>(disposables.length * 4 / 3 + 1);
        for (Disposable t : disposables) {
            this.disposables.add(t);
        }
    }
    
    // TODO javadoc
    public CompositeDisposable(Iterable<? extends Disposable> disposables) {
        Objects.requireNonNull(disposables);
        this.disposables = new HashSet<>();
        disposables.forEach(this.disposables::add);
    }
    
    /**
     * Adds a new {@link Disposable} to this {@code CompositeDisposable} if the
     * {@code CompositeDisposable} is not yet disposed. If the {@code CompositeDisposable} <em>is</em>
     * unsubscribed, {@code add} will indicate this by explicitly disposing the new {@code Disposable} as
     * well.
     *
     * @param s
     *          the {@link Disposable} to add
     */
    public void add(Disposable resource) {
        if (resource.isDisposed()) {
            return;
        }
        
        if (!disposed) {
            synchronized (this) {
                if (!disposed) {
                    Set<Disposable> rs = disposables;
                    if (rs == null) {
                        rs = new HashSet<>(4);
                        disposables = rs;
                    }
                    rs.add(resource);
                    return;
                }
            }
        }
        resource.dispose();
    }
    
    /**
     * Removes a {@link Disposable} from this {@code CompositeDisposable}, and unsubscribes the
     * {@link Disposable}.
     *
     * @param s
     *          the {@link Disposable} to remove
     */
    public void remove(Disposable resource) {
        if (!disposed) {
            synchronized (this) {
                if (disposed || disposables == null || !disposables.remove(resource)) {
                    return;
                }
            }
            resource.dispose();
        }
    }
    
    /**
     * Unsubscribes any Disposables that are currently part of this {@code CompositeDisposable} and remove
     * them from the {@code CompositeDisposable} so that the {@code CompositeDisposable} is empty and in
     * an unoperative state.
     */
    public void clear() {
        if (!disposed) {
            clearOrCancel(false);
        }
    }
    
    private void clearOrCancel(boolean cancel) {
        Collection<Disposable> coll;
        synchronized (this) {
            if (disposed) {
                return;
            }
            coll = disposables;
            disposables = null;
            if (cancel) {
                disposed = true;
            }
        }
        if (coll != null) {
            coll.forEach(Disposable::dispose);
        }
        
    }
    
    /**
     * Returns true if this composite is not unsubscribed and contains Disposables.
     *
     * @return {@code true} if this composite is not unsubscribed and contains Disposables.
     * @since 2.0.0
     */
    public boolean hasDisposables() {
        if (!disposed) {
            synchronized (this) {
                return !disposed && disposables != null && !disposables.isEmpty();
            }
        }
        return false;
    }
    
    @Override
    public boolean isDisposed() {
        return disposed;
    }
    @Override
    public void dispose() {
        if (!disposed) {
            clearOrCancel(true);
        }
    }
}
