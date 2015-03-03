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
package rxjf.internal;

import static rxjf.internal.UnsafeAccess.*;

import java.util.Objects;
import java.util.function.IntFunction;

import rxjf.disposables.Disposable;

/**
 * Base class for managing an array of values with copy-on-write semantics and with the
 * ability to "terminate" the array, rejecting any further interaction.
 * <p>
 * Note: for internal use only since it exposes the underlying array direcly for performance reasons.
 */
public class AbstractArrayManager<T> implements Disposable {
    /** Factory to create new arrays with the correct base type. */
    private final IntFunction<T[]> newArray;
    
    private volatile T[] array;
    private static final long ARRAY = addressOf(AbstractArrayManager.class, "array");
    
    /** Indicates an empty array. */
    private final T[] empty;
    /** Indicates an terminated array. */
    protected final T[] terminated;
    
    public AbstractArrayManager(IntFunction<T[]> newArray) {
        this.newArray = newArray;
        this.empty = newArray.apply(0);
        this.terminated = newArray.apply(0);
        UNSAFE.putOrderedObject(this, ARRAY, empty);
    }
    
    public final boolean add(T value) {
        for (;;) {
            T[] curr = array;
            if (curr == terminated) {
                return false;
            }
            int n = curr.length;
            int n2 = n + 1;
            
            T[] next = newArray.apply(n2);
            System.arraycopy(curr, 0, next, 0, n);
            next[n] = value;
            
            if (UNSAFE.compareAndSwapObject(this, ARRAY, curr, next)) {
                return true;
            }
        }
    }
    
    public final boolean remove(T value) {
        for (;;) {
            T[] curr = array;
            if (curr == terminated || curr == empty) {
                return false;
            }
            
            int j = -1;
            int n = curr.length;
            for (int i = 0; i < n; i++) {
                if (Objects.equals(curr[i], value)) {
                    j = i;
                    break;
                }
            }
            if (j < 0) {
                return false;
            }
            
            T[] next;
            int n2 = n - 1;
            if (n2 == 0) {
                next = empty;
            } else {
                next = newArray.apply(n2);
                System.arraycopy(curr, 0, next, 0, j);
                System.arraycopy(curr, j + 1, next, j, n2 - j);
            }
            if (UNSAFE.compareAndSwapObject(this, ARRAY, curr, next)) {
                return true;
            }
        }
    }
    /**
     * Terminates this manager and returns the last array just before the termination.
     * Calling this on a terminated manager will return an empty array.
     * @return the last array before the termination
     */
    @SuppressWarnings("unchecked")
    public final T[] getAndTerminate() {
        T[] t = terminated;
        T[] a = array;
        if (a == t) {
            return a;
        }
        return (T[])UNSAFE.getAndSetObject(this, ARRAY, t);
    }
    public final T[] array() {
        return array;
    }
    @Override
    public final void dispose() {
        T[] t = terminated;
        T[] a = array;
        if (a != t) {
            UNSAFE.putObjectVolatile(this, ARRAY, terminated);
        }
    }
    @Override
    public final boolean isDisposed() {
        return array == terminated;
    }
}
