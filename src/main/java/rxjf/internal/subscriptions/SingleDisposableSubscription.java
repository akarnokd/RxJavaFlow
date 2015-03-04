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
package rxjf.internal.subscriptions;

import static rxjf.internal.UnsafeAccess.*;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.disposables.Disposable;
import rxjf.internal.TerminalAtomics;
/**
 * Holds onto a single disposable reference.
 */
public final class SingleDisposableSubscription implements Subscription, Disposable {
    final Subscription actual;
    
    volatile Disposable disposable;
    static final long DISPOSABLE = addressOf(SingleDisposableSubscription.class, "disposable");
    
    public SingleDisposableSubscription(Subscription actual) {
        this.actual = actual;
    }
    
    @Override
    public void request(long n) {
        actual.request(n);
    }
    
    @Override
    public void dispose() {
        cancel();
    }
    @Override
    public void cancel() {
        TerminalAtomics.dispose(this, DISPOSABLE);
        actual.cancel();
    }
    @Override
    public boolean isDisposed() {
        return TerminalAtomics.isDisposed(this, DISPOSABLE);
    }
    public void set(Disposable d) {
        TerminalAtomics.set(this, DISPOSABLE, d);
    }
    
    /**
     * Creates an SingleDisposableSubscription whose actual Subscription is the empty
     * subscription associated with the given child.
     * @param child
     * @return
     */
    public static <T> SingleDisposableSubscription createEmpty(Subscriber<? super T> child) {
        return new SingleDisposableSubscription(AbstractSubscription.createEmpty(child));
    }
}
