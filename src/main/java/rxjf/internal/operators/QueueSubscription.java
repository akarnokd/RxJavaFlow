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

import static rxjf.internal.UnsafeAccess.*;

import java.util.*;

import rxjf.Flow.Subscriber;
import rxjf.internal.*;
/**
 * 
 */
public final class QueueSubscription<T> extends AbstractSubscription<T> {
    final Queue<Object> queue;
    final NotificationLite<T> nl = NotificationLite.instance();
    volatile Object terminal;
    static final long TERMINAL = addressOf(QueueSubscription.class, "terminal");
    
    public QueueSubscription(Subscriber<? super T> subscriber) {
        this(subscriber, new ArrayDeque<>());
    }
    public QueueSubscription(Subscriber<? super T> subscriber, Queue<Object> queue) {
        super(subscriber);
        this.queue = queue;
    }
    
    @Override
    protected void onRequested(long n) {
        drain();
    }
    /**
     * <p>
     * Methods offer and terminate should be called non-concurrently.
     * @param value
     * @return
     */
    public boolean offer(T value) {
        if (queue.offer(nl.next(value))) {
            drain();
            return true;
        }
        return false;
    }
    /**
     * <p>
     * Methods offer and terminate should be called non-concurrently.
     */
    public void complete() {
        UNSAFE.putOrderedObject(this, TERMINAL, nl.complete());
        drain();
    }
    /**
     * <p>
     * Methods offer and terminate should be called non-concurrently.
     */
    public void error(Throwable t) {
        UNSAFE.putOrderedObject(this, TERMINAL, nl.error(t));
        drain();
    }
    
    public void drain() {
        // TODO
    }
}
