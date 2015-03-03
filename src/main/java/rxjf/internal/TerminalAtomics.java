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

import static rxjf.internal.UnsafeAccess.UNSAFE;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.disposables.Disposable;

/**
 * Utility methods to work with terminatable atomics operations on
 * objects (disposables, copy-on-write objects) and longs (requests).
 * <p>
 * Since this class uses Unsafe atomics, be extra careful.
 */
public final class TerminalAtomics {
    /** Utility class. */
    private TerminalAtomics() { 
        throw new IllegalStateException("No instances!");
    }
    
    // ---------------------
    // Disposable management
    // ---------------------
    
    /** The common terminated state indicator. */
    private static final Disposable TERMINATED = new Disposable() {
        @Override
        public void dispose() {
            
        }
        @Override
        public boolean isDisposed() {
            return false;
        }
    };
    
    /**
     * Atomically sets the disposable on the target, disposes any previous value and
     * returns true; or if the target contains the common TERMINATED value, disposes
     * the disposable and returns false.
     * @param target
     * @param address
     * @param disposable
     * @return true if the store succeeded, false if the target contained TERMINATED
     */
    public static boolean set(Object target, long address, Disposable disposable) {
        for (;;) {
            Object o = UNSAFE.getObjectVolatile(target, address);
            if (o == TERMINATED) {
                disposable.dispose();
                return false;
            }
            if (UNSAFE.compareAndSwapObject(target, address, o, disposable)) {
                Disposable d = (Disposable)o;
                if (d != null) {
                    d.dispose();
                }
                return true;
            }
        }
    }
    
    /**
     * Atomically replaces the disposable in the target with the provided disposable and
     * returns the previous value; or if the target contains the common TERMINATED value, it
     * disposes the disposable and returns it.
     * @param target
     * @param address
     * @param disposable
     * @return
     */
    public static Disposable replace(Object target, long address, Disposable disposable) {
        for (;;) {
            Disposable o = (Disposable)UNSAFE.getObjectVolatile(target, address);
            if (o == TERMINATED) {
                o.dispose();
                return o;
            }
            if (UNSAFE.compareAndSwapObject(target, address, o, disposable)) {
                return o;
            }
        }
    }
    
    /**
     * Returns the current disposable or the Disposable.DISPOSED if the
     * target location contains the common terminated disposable.
     * @param target
     * @param address
     * @return
     */
    public static Disposable get(Object target, long address) {
        Object o = UNSAFE.getObjectVolatile(target, address);
        if (o == TERMINATED) {
            return Disposable.DISPOSED;
        }
        return (Disposable)o;
    }
    
    /**
     * Replaces the Disposable at the target address with the common 
     * TERMINATED instance and disposes its previous content.
     * @param target
     * @param address
     * @return true if
     */
    public static boolean dispose(Object target, long address) {
        Object o = UNSAFE.getObjectVolatile(target, address);
        if (o != TERMINATED) {
            Disposable d = (Disposable)UNSAFE.getAndSetObject(target, address, TERMINATED);
            if (d != null && d != TERMINATED) {
                d.dispose();
                return true;
            }
        }
        return false;
    }
    /**
     * Checks if the target location contains the common TERMINATED disposable
     * instance.
     * @param target
     * @param address
     * @return
     */
    public static boolean isDisposed(Object target, long address) {
        return TERMINATED == UNSAFE.getObjectVolatile(target, address);
    }

    // -------------------------
    // General object management
    // -------------------------

    /**
     * Tries to atomically replace the target and returns the previous
     * value if successful or the terminalIndicator.
     * @param target
     * @param address
     * @param value
     * @param terminalIndicator
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T replace(Object target, long address, T value, T terminalIndicator) {
        for (;;) {
            Object o = UNSAFE.getObjectVolatile(target, address);
            if (o == terminalIndicator) {
                return terminalIndicator;
            }
            if (UNSAFE.compareAndSwapObject(target, address, o, value)) {
                return (T)o;
            }
        }
    }
    
    /**
     * Tries to atomically set the value at the target address if
     * not already containing the terminalIndicator value. 
     * @param target
     * @param address
     * @param value
     * @param terminalIndicator
     * @return
     */
    public static <T> boolean set(Object target, long address, 
            T value, T terminalIndicator) {
        for (;;) {
            Object o = UNSAFE.getObjectVolatile(target, address);
            if (o == terminalIndicator) {
                return false;
            }
            if (UNSAFE.compareAndSwapObject(target, address, o, value)) {
                return true;
            }
        }
    }
    /**
     * Atomically swaps in the terminalIndicator and returns true if the address
     * didn't contain the terminalIndicator before.
     * @param target
     * @param address
     * @param terminalIndicator
     * @return
     */
    public static <T> boolean terminate(Object target, long address, T terminalIndicator) {
        Object o = UNSAFE.getObjectVolatile(target, address);
        if (o == terminalIndicator) {
            return false;
        }
        o = UNSAFE.getAndSetObject(target, address, terminalIndicator);
        return o != terminalIndicator;
    }
    
    /**
     * Atomically swaps in the terminalIndicator and returns the last value found.
     * @param target
     * @param address
     * @param terminalIndicator
     * @return
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAndTerminate(Object target, long address, T terminalIndicator) {
        Object o = UNSAFE.getObjectVolatile(target, address);
        if (o == terminalIndicator) {
            return (T)o;
        }
        return (T)UNSAFE.getAndSetObject(target, address, terminalIndicator);
    }
    // -------------------------
    // Request management
    // -------------------------
    
    /** Indicates a cancelled request counter. */
    public static final long CANCELLED = Long.MIN_VALUE;
    /** Indicates that no request has been made yet. */
    public static final long NO_REQUEST = Long.MIN_VALUE / 2;
    
    /**
     * Atomically adds the amount n to the target value (caps it at Long.MAX_VALUE) unless it contains
     * the CANCELLED indicator and returns the original value.
     * <p>
     * Note: does not validate n
     * @param target
     * @param address
     * @param n the request amount, make sure n > 0
     * @return the previous value or CANCELLED
     */
    public static long request(Object target, long address, long n) {
        for (;;) {
            long r = UNSAFE.getLongVolatile(target, address);
            if (r == CANCELLED) {
                return r;
            }
            long u = r + n;
            if (u < 0) {
                u = Long.MAX_VALUE;
            }
            if (UNSAFE.compareAndSwapLong(target, address, r, u)) {
                return r;
            }
        }
    }
    /**
     * Atomically adds the amount n to the target value (caps it at Long.MAX_VALUE) unless it contains
     * the CANCELLED indicator and returns the original value, and handles the
     * case where the current value is the NO_REQUEST.
     * <p>
     * Note: does not validate n!
     * @param target
     * @param address
     * @param n the request amount, make sure n > 0
     * @return the previous value (NO_REQUEST is treated as 0) or CANCELLED
     */
    public static long requestUnrequested(Object target, long address, long n) {
        for (;;) {
            long r = UNSAFE.getLongVolatile(target, address);
            if (r == CANCELLED) {
                return r;
            }
            long u;
            if (r == NO_REQUEST) {
                u = n;
            } else {
                u = r + n;
                if (u < 0) {
                    u = Long.MAX_VALUE;
                }
            }
            if (UNSAFE.compareAndSwapLong(target, address, r, u)) {
                return r;
            }
        }
    }
    
    /**
     * Atomically decrements the value at the given location unless it
     * contains the CANCELLED indicator.
     * <p>
     * Note: does not validate n and doesn't check for underflow!
     * @param target
     * @param address
     * @param n the produced amount, make sure n > 0
     * @return the new value (after decrement) or CANCELLED
     */
    public static long produced(Object target, long address, long n) {
        for (;;) {
            long r = UNSAFE.getLongVolatile(target, address);
            if (r == CANCELLED) {
                return r;
            }
            long u = r - n;
            if (UNSAFE.compareAndSwapLong(target, address, r, u)) {
                return u;
            }
        }
    }
    
    /**
     * Atomically decrements the value at the given location unless it
     * contains the CANCELLED indicator and reports an underflow (more produced than requested)
     * through the given Subscriber and cancels the Subscription.
     * @param target
     * @param address
     * @param n
     * @param onError
     * @param onErrorCancel
     * @return the new value (after decrement) or CANCELLED
     */
    public static long producedChecked(Object target, long address, long n, 
            Subscriber<?> onError, Subscription onErrorCancel) {
        for (;;) {
            long r = UNSAFE.getLongVolatile(target, address);
            if (r == CANCELLED) {
                return r;
            }
            long u = r - n;
            if (u < 0) {
                onError.onError(new IllegalArgumentException("More produced (" + n + " than requested (" + r + ")!"));
                onErrorCancel.cancel();
                return CANCELLED;
            }
            if (UNSAFE.compareAndSwapLong(target, address, r, u)) {
                return u;
            }
        }
    }
    /**
     * Atomically swaps in the CANCELLED state and returns true the
     * target address didn't contain CANCELLED.
     * @param target
     * @param address
     * @return true if the original value was not the CANCELLED value
     */
    public static boolean cancel(Object target, long address) {
        long o = UNSAFE.getLongVolatile(target, address);
        if (o == CANCELLED) {
            return false;
        }
        return CANCELLED != UNSAFE.getAndSetLong(target, address, CANCELLED);
    }
}
