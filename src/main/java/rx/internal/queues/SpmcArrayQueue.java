/*
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
 * 
 * Original License: https://github.com/JCTools/JCTools/blob/master/LICENSE
 * Original location: https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/SpmcArrayQueue.java
 */
package rx.internal.queues;

import static rx.internal.UnsafeAccess.*;
import sun.misc.Contended;


public final class SpmcArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {
    protected final static long P_INDEX_OFFSET = addressOf(SpmcArrayQueue.class, "producerIndex");
    @Contended("producer")
    private volatile long producerIndex;

    protected final static long C_INDEX_OFFSET = addressOf(SpmcArrayQueue.class, "consumerIndex");
    @Contended("consumer")
    private volatile long consumerIndex;

    // This is separated from the consumerIndex which will be highly contended in the hope that this value spends most
    // of it's time in a cache line that is Shared(and rarely invalidated)
    @Contended("producerIndexCache")
    private volatile long producerIndexCache;

    public SpmcArrayQueue(final int capacity) {
        super(capacity);
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException("Null is not a valid element");
        }
        final E[] lb = buffer;
        final long lMask = mask;
        final long currProducerIndex = lvProducerIndex();
        final long offset = calcElementOffset(currProducerIndex);
        if (null != lvElement(lb, offset)) {
            long size = currProducerIndex - lvConsumerIndex();
            
            if(size > lMask) {
                return false;
            }
            else {
                // spin wait for slot to clear, buggers wait freedom
                while(null != lvElement(lb, offset));
            }
        }
        spElement(lb, offset, e);
        // single producer, so store ordered is valid. It is also required to correctly publish the element
        // and for the consumers to pick up the tail value.
        soTail(currProducerIndex + 1);
        return true;
    }

    @Override
    public E poll() {
        long currentConsumerIndex;
        final long currProducerIndexCache = lvProducerIndexCache();
        do {
            currentConsumerIndex = lvConsumerIndex();
            if (currentConsumerIndex >= currProducerIndexCache) {
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex) {
                    return null;
                } else {
                    svProducerIndexCache(currProducerIndex);
                }
            }
        } while (!casHead(currentConsumerIndex, currentConsumerIndex + 1));
        // consumers are gated on latest visible tail, and so can't see a null value in the queue or overtake
        // and wrap to hit same location.
        final long offset = calcElementOffset(currentConsumerIndex);
        final E[] lb = buffer;
        // load plain, element happens before it's index becomes visible
        final E e = lpElement(lb, offset);
        // store ordered, make sure nulling out is visible. Producer is waiting for this value.
        soElement(lb, offset, null);
        return e;
    }

    @Override
    public E peek() {
        long currentConsumerIndex;
        final long currProducerIndexCache = lvProducerIndexCache();
        E e;
        do {
            currentConsumerIndex = lvConsumerIndex();
            if (currentConsumerIndex >= currProducerIndexCache) {
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex) {
                    return null;
                } else {
                    svProducerIndexCache(currProducerIndex);
                }
            }
        } while (null == (e = lvElement(calcElementOffset(currentConsumerIndex))));
        return e;
    }

    @Override
    public int size() {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and consumer
         * indices, therefore protection is required to ensure size is within valid range. In the event of concurrent
         * polls/offers to this method the size is OVER estimated as we read consumer index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        while (true) {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after) {
                return (int) (currentProducerIndex - after);
            }
        }
    }
    
    @Override
    public boolean isEmpty() {
        // Order matters! 
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures the correctness of this method at least for the consumer thread. Other threads POV is not really
        // something we can fix here.
        return (lvConsumerIndex() == lvProducerIndex());
    }

    protected final void soTail(long v) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, v);
    }

    protected final long lvConsumerIndex() {
        return consumerIndex;
    }

    protected final boolean casHead(long expect, long newValue) {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }

    protected final long lvProducerIndexCache() {
        return producerIndexCache;
    }

    protected final void svProducerIndexCache(long v) {
        producerIndexCache = v;
    }

    protected final long lvProducerIndex() {
        return producerIndex;
    }
}
