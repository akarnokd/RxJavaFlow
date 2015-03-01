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

import rxjf.Flow.Subscriber;
import rxjf.Flowable.OnSubscribe;
import rxjf.internal.AbstractSubscription;

/**
 * 
 */
public final class OnSubscribeRange implements OnSubscribe<Integer> {
    final int start;
    final int count;
    public OnSubscribeRange(int start, int count) {
        this.start = start;
        this.count = count;
    }
    @Override
    public void accept(Subscriber<? super Integer> child) {
        int c = count;
        int s = start;
        child.onSubscribe(new AbstractSubscription<Integer>(child) {
            int remaining = c;
            int value = s;
            @Override
            protected void onRequested(long n) {
                int v = value;
                int r = remaining;
                if (n == Long.MAX_VALUE) {
                    while (r > 0) {
                        if (isDisposed()) {
                            return;
                        }
                        child.onNext(v++);
                        r--;
                    }
                    child.onComplete();
                    return;
                }
                long r0 = n;
                for (;;) {
                    long c = r0;
                    while (r0 > 0 && r > 0) {
                        if (isDisposed()) {
                            return;
                        }
                        child.onNext(v++);
                        r0--;
                        r--;
                    }
                    if (r == 0) {
                        child.onComplete();
                        break;
                    } else {
                        value = v;
                        remaining = r;
                        r0 = produced(c);
                    }
                    if (r0 == 0) {
                        break;
                    }
                }
            }
        });
    }
}
