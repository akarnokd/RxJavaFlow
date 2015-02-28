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

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * All use of this class MUST first check that UnsafeAccess.isUnsafeAvailable() == true
 * otherwise NPEs will happen in environments without "suc.misc.Unsafe" such as Android.
 */
public final class UnsafeAccess {
    private UnsafeAccess() {
        throw new IllegalStateException("No instances!");
    }

    public static final Unsafe UNSAFE;
    static {
        Unsafe u = null;
        try {
            /*
             * This mechanism for getting UNSAFE originally from:
             * 
             * Original License: https://github.com/JCTools/JCTools/blob/master/LICENSE
             * Original location: https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/util/UnsafeAccess.java
             */
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            u = (Unsafe) field.get(null);
        } catch (Throwable e) {
            // do nothing, hasUnsafe() will return false
        }
        UNSAFE = u;
    }

    public static final boolean isUnsafeAvailable() {
        return UNSAFE != null;
    }
    public static long addressOf(Class<?> clazz, String field) {
        try {
            return UNSAFE.objectFieldOffset(clazz.getDeclaredField(field));
        } catch (NoSuchFieldException | NullPointerException ex) {
            throw new InternalError(ex);
        }
    }
}