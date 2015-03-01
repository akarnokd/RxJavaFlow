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
package rxjf.disposables;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.*;

import rxjf.internal.disposables.BooleanDisposable;

public class MultipleAssignmentDisposableTest {

    Runnable unsubscribe;
    Disposable s;

    @Before
    public void before() {
        unsubscribe = mock(Runnable.class);
        s = new BooleanDisposable(unsubscribe);
    }

    @Test
    public void testNoUnsubscribeWhenReplaced() {
        MultipleAssignmentDisposable mas = new MultipleAssignmentDisposable();

        mas.set(s);
        mas.set(new BooleanDisposable());
        mas.dispose();

        verify(unsubscribe, never()).run();
    }

    @Test
    public void testUnsubscribeWhenParentUnsubscribes() {
        MultipleAssignmentDisposable mas = new MultipleAssignmentDisposable();
        mas.set(s);
        mas.dispose();
        mas.dispose();

        verify(unsubscribe, times(1)).run();

        Assert.assertEquals(true, mas.isDisposed());
    }

    @Test
    public void subscribingWhenUnsubscribedCausesImmediateUnsubscription() {
        MultipleAssignmentDisposable mas = new MultipleAssignmentDisposable();
        mas.dispose();
        Disposable underlying = mock(Disposable.class);
        mas.set(underlying);
        verify(underlying).dispose();
    }

    // FIXME behavior change
    @Test
    public void testDisposableRemainsAfterUnsubscribe() {
        MultipleAssignmentDisposable mas = new MultipleAssignmentDisposable();

        mas.set(s);
        mas.dispose();

        Assert.assertTrue(mas.get() != s);
    }
}