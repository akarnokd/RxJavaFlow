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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;

import rx.internal.disposables.BooleanDisposable;

public class DisposablesTest {

    @Test
    public void testUnsubscribeOnlyOnce() {
        Runnable unsubscribe = mock(Runnable.class);
        Disposable subscription = new BooleanDisposable(unsubscribe);
        subscription.dispose();
        subscription.dispose();
        verify(unsubscribe, times(1)).run();
    }

    @Test
    public void testEmpty() {
        Disposable empty = new BooleanDisposable();
        assertFalse(empty.isDisposed());
        empty.dispose();
        assertTrue(empty.isDisposed());
    }

    @Test
    public void testUnsubscribed() {
        Disposable unsubscribed = Disposable.DISPOSED;
        assertTrue(unsubscribed.isDisposed());
    }
}
