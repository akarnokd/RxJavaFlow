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

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 
 */
public final class CompositeCancellable implements Cancellable {
    volatile boolean cancelled;
    /** Guarded by this. */
    Set<Cancellable> cancellables;
    public CompositeCancellable() {
        
    }
    @SafeVarargs
    public CompositeCancellable(Cancellable... cancellables) {
        if (cancellables == null) {
            throw new NullPointerException();
        }
        this.cancellables = new HashSet<>(cancellables.length * 4 / 3 + 1);
        for (Cancellable t : cancellables) {
            this.cancellables.add(t);
        }
    }
    
    public CompositeCancellable(Iterable<? extends Cancellable> cancellables) {
        if (cancellables == null) {
            throw new NullPointerException();
        }
        this.cancellables = new HashSet<>();
        cancellables.forEach(this.cancellables::add);
    }
    
    public void add(Cancellable resource) {
        if (resource.isCancelled()) {
            return;
        }
        
        if (!cancelled) {
            synchronized (this) {
                if (!cancelled) {
                    Set<Cancellable> rs = cancellables;
                    if (rs == null) {
                        rs = new HashSet<>(4);
                        cancellables = rs;
                    }
                    rs.add(resource);
                }
            }
        }
        resource.cancel();
    }
    
    public void remove(Cancellable resource) {
        if (!cancelled) {
            synchronized (this) {
                if (cancelled || cancellables == null || !cancellables.remove(resource)) {
                    return;
                }
            }
            resource.cancel();
        }
    }
    
    public void clear() {
        if (!cancelled) {
            clearOrCancel(false);
        }
    }
    
    private void clearOrCancel(boolean cancel) {
        Collection<Cancellable> coll;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            coll = cancellables;
            cancellables = null;
            if (cancel) {
                cancelled = true;
            }
        }
        if (coll != null) {
            coll.forEach(Cancellable::cancel);
        }
        
    }
    
    public boolean hasCancellables() {
        if (!cancelled) {
            synchronized (this) {
                return !cancelled && cancellables != null && !cancellables.isEmpty();
            }
        }
        return false;
    }
    
    @Override
    public boolean isCancelled() {
        return cancelled;
    }
    @Override
    public void cancel() {
        if (!cancelled) {
            clearOrCancel(true);
        }
    }
}
