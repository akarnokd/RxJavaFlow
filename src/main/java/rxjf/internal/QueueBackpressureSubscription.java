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

import java.util.Queue;

import rxjf.*;
import rxjf.Flow.Subscriber;
/**
 * A subscription, backed by a Queue, that coordinates value production (offer) and
 * value emission (onXXX) for a subscriber while respecting the subscriber's requests.
 */
public final class QueueBackpressureSubscription<T> extends AbstractBackpressureSubscription<T> {
    /** The queue holding the normal events. */
    final Queue<T> queue;
    /** Stores the terminal event object as returned by the NotificationLite. */
    /**
     * Constructs a QueueSubscription with the given subscriber and an SpscArrayQueue 
     * with capacity Flow.defaultBufferSize().
     * @param subscriber the subscriber to emit values to
     */
    public QueueBackpressureSubscription(Subscriber<? super T> subscriber) {
        this(subscriber, new SpscArrayQueue<>(Flow.defaultBufferSize()));
    }
    /**
     * TODO
     * @param subscriber
     * @param queue
     */
    public QueueBackpressureSubscription(Subscriber<? super T> subscriber, Queue<T> queue) {
        super(subscriber);
        this.queue = queue;
    }
    
    @Override
    protected boolean offer(T value) {
        return queue.offer(value);
    }
    
    @Override
    protected T peek() {
        return queue.peek();
    }
    @Override
    protected T poll() {
        return queue.poll();
    }
}
