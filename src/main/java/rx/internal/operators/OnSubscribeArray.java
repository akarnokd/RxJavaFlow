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

package rx.internal.operators;

import java.util.Objects;

import rx.Flow.Subscriber;
import rx.Observable.OnSubscribe;
import rx.internal.subscriptions.AbstractSubscription;

/**
 * 
 */
public final class OnSubscribeArray<T> implements OnSubscribe<T> {
    final T[] array;
    @SafeVarargs
    public OnSubscribeArray(T... array) {
        this.array = Objects.requireNonNull(array);
    }
    @Override
    public void accept(Subscriber<? super T> t) {
        if (array.length == 0) {
            AbstractSubscription.setEmptyOn(t);
            t.onComplete();
            return;
        }
        t.onSubscribe(new AbstractSubscription<T>(t) {
            int index = 0;
            @Override
            protected void onRequested(long n) {
                if (n == Long.MAX_VALUE) {
                    for (T e : array) {
                        if (isDisposed()) {
                            return;
                        }
                        t.onNext(e);
                    }
                    t.onComplete();
                    return;
                }
                long r0 = n;
                T[] a = array;
                for (;;) {
                    long c = r0;
                    while (r0 > 0 && index < a.length) {
                        if (isDisposed()) {
                            return;
                        }
                        t.onNext(a[index]);
                        r0--;
                        index++;
                    }
                    if (index == a.length) {
                        t.onComplete();
                        break;
                    } else {
                        r0 = produced(c);
                    }
                    if (r0 <= 0) {
                        break;
                    }
                }
            }
        });
    }
}
