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
 * Original location: https://github.com/JCTools/JCTools/blob/master/jctools-core/src/main/java/org/jctools/queues/SpscArrayQueue.java
 */
package rxjf.internal.queues;

import static rxjf.internal.UnsafeAccess.*;
import sun.misc.Contended;


/**
 * A Single-Producer-Single-Consumer queue backed by a pre-allocated buffer.
 * <p>
 * This implementation is a mashup of the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * algorithm with an optimization of the offer method taken from the <a
 * href="http://staff.ustc.edu.cn/~bhua/publications/IJPP_draft.pdf">BQueue</a> algorithm (a variation on Fast
 * Flow), and adjusted to comply with Queue.offer semantics with regards to capacity.<br>
 * For convenience the relevant papers are available in the resources folder:<br>
 * <i>2010 - Pisa - SPSC Queues on Shared Cache Multi-Core Systems.pdf<br>
 * 2012 - Junchang- BQueue- Efficient and Practical Queuing.pdf <br>
 * </i> This implementation is wait free.
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public final class SpscArrayQueue<E> extends ConcurrentCircularArrayQueue<E> {
    private static final Integer MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);
    protected final int lookAheadStep;
    
    protected final static long P_INDEX_OFFSET = addressOf(SpscArrayQueue.class, "producerIndex");
    protected final static long C_INDEX_OFFSET = addressOf(SpscArrayQueue.class, "consumerIndex");
    
    @Contended("producer")
    protected long producerIndex;
    @Contended("producer")
    protected long producerLookAhead;
    @Contended("consumer")
    protected long consumerIndex;
    
    public SpscArrayQueue(int capacity) {
        super(capacity);
        lookAheadStep = Math.min(capacity/4, MAX_LOOK_AHEAD_STEP);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e) {
        // local load of field to avoid repeated loads after volatile reads
        final E[] lElementBuffer = buffer;
        final long index = producerIndex;
        final long offset = calcElementOffset(index);
        if (null != lvElement(lElementBuffer, offset)){
            return false;
        }
        soProducerIndex(index + 1); // ordered store -> atomic and ordered for size()
        soElement(lElementBuffer, offset, e); // StoreStore
        return true;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E poll() {
        final long index = consumerIndex;
        final long offset = calcElementOffset(index);
        // local load of field to avoid repeated loads after volatile reads
        final E[] lElementBuffer = buffer;
        final E e = lvElement(lElementBuffer, offset);// LoadLoad
        if (null == e) {
            return null;
        }
        soConsumerIndex(index + 1); // ordered store -> atomic and ordered for size()
        soElement(lElementBuffer, offset, null);// StoreStore
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E peek() {
        return lvElement(calcElementOffset(consumerIndex));
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

    private void soProducerIndex(long v) {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, v);
    }

    private void soConsumerIndex(long v) {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, v);
    }
    
    private long lvProducerIndex() {
        return UNSAFE.getLongVolatile(this, P_INDEX_OFFSET);
    }
    
    private long lvConsumerIndex() {
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }
}

