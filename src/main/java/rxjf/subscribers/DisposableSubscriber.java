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

package rxjf.subscribers;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.Conformance;
/**
 * 
 */
public final class DisposableSubscriber<T> extends AbstractDisposableSubscriber<T> {
    final Subscriber<? super T> actual;
    
    public DisposableSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    /**
     * Wraps a regual Subscriber to a DisposableSubscriber or returns it
     * directly if already the right type.
     * @param actual
     * @return
     */
    public static <T> AbstractDisposableSubscriber<T> wrap(Subscriber<T> actual) {
        Conformance.subscriberNonNull(actual);
        if (actual instanceof AbstractDisposableSubscriber) {
            return (DisposableSubscriber<T>)actual;
        }
        return new DisposableSubscriber<>(actual);
    }
    @Override
    protected void internalOnSubscribe(Subscription subscription) {
        actual.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                subscription.request(n);
            }
            @Override
            public void cancel() {
                DisposableSubscriber.this.dispose();
            }
        });
    }
    @Override
    protected void internalOnNext(T item) {
        actual.onNext(item);
    }
    @Override
    protected void internalOnError(Throwable throwable) {
        actual.onError(throwable);
    }
    @Override
    protected void internalOnComplete() {
        actual.onComplete();
    }
}
