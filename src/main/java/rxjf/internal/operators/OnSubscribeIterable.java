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

package rxjf.internal.operators;

import java.util.Iterator;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.OnSubscribe;
import rxjf.internal.AbstractSubscription;

/**
 * 
 */
public final class OnSubscribeIterable<T> implements OnSubscribe<T> {
    final Iterable<? extends T> iterable;
    public OnSubscribeIterable(Iterable<? extends T> iterable) {
        this.iterable = iterable;
    }
    @Override
    public void accept(Subscriber<? super T> t) {
        Iterator<? extends T> it = iterable.iterator();
        if (!it.hasNext()) {
            t.onSubscribe(AbstractSubscription.createEmpty(t));
            t.onComplete();
            return;
        }
        t.onSubscribe(AbstractSubscription.create(t, (r, s) -> {
            if (r == Long.MAX_VALUE) {
                while (it.hasNext()) {
                    if (s.isDisposed()) {
                        return;
                    }
                    t.onNext(it.next());
                }
                t.onComplete();
                return;
            }
            long r0 = r;
            for (;;) {
                long c = r0;
                while (r0 > 0 && it.hasNext()) {
                    if (s.isDisposed()) {
                        return;
                    }
                    t.onNext(it.next());
                    r0--;
                }
                if (!it.hasNext()) {
                    t.onComplete();
                    break;
                } else {
                    r0 = s.produced(c);
                }
                if (r0 == 0) {
                    break;
                }
            }
        }));
    }
}
