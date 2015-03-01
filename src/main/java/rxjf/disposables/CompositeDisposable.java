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
 * 
 */
public final class CompositeDisposable implements Disposable {
    volatile boolean disposed;
    /** Guarded by this. */
    Set<Disposable> disposables;
    public CompositeDisposable() {
        
    }
    @SafeVarargs
    public CompositeDisposable(Disposable... disposables) {
        Objects.requireNonNull(disposables);
        this.disposables = new HashSet<>(disposables.length * 4 / 3 + 1);
        for (Disposable t : disposables) {
            this.disposables.add(t);
        }
    }
    
    public CompositeDisposable(Iterable<? extends Disposable> disposables) {
        Objects.requireNonNull(disposables);
        this.disposables = new HashSet<>();
        disposables.forEach(this.disposables::add);
    }
    
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
                }
            }
        }
        resource.dispose();
    }
    
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
