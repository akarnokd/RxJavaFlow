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
 */
package rxjf.internal;

import static rxjf.internal.UnsafeAccess.*;

import java.util.Objects;
/**
 * This is a direct Java port of the MPSC algorithm as presented <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on 1024
 * Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory model and
 * layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>Use XCHG functionality to the best of the JDK ability (see differences in JDK7/8 impls).
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references. From this
 * point follow the notes on offer/poll.
 * 
 * @author nitsanw
 * 
 * @param <E>
 */
public final class MpscLinkedQueue<E> extends BaseLinkedQueue<E> {
    public MpscLinkedQueue() {
        consumerNode = new LinkedQueueNode<>();
        xchgProducerNode(consumerNode);// this ensures correct construction: StoreLoad
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from multiple threads.<br>
     * Offer allocates a new node and:
     * <ol>
     * <li>Swaps it atomically with current producer node (only one producer 'wins')
     * <li>Sets the new node as the node following from the swapped producer node
     * </ol>
     * This works because each producer is guaranteed to 'plant' a new node and link the old node. No 2 producers can
     * get the same producer node as part of XCHG guarantee.
     * 
     * @see MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public final boolean offer(final E nextValue) {
        Objects.requireNonNull(nextValue);
        final LinkedQueueNode<E> nextNode = new LinkedQueueNode<>(nextValue);
        final LinkedQueueNode<E> prevProducerNode = xchgProducerNode(nextNode);
        // Should a producer thread get interrupted here the chain WILL be broken until that thread is resumed
        // and completes the store in prev.next.
        prevProducerNode.soNext(nextNode); // StoreStore
        return true;
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is null, the queue is assumed empty (though it might not be).
     * <li>If it is not null set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always null, which is also the starting point for the queue. Because null
     * values are not allowed to be offered this is the only node with it's value set to null at any one time.
     * 
     * @see MessagePassingQueue#poll()
     * @see java.util.Queue#poll()
     */
    @Override
    public final E poll() {
        LinkedQueueNode<E> currConsumerNode = lpConsumerNode(); // don't load twice, it's alright
        LinkedQueueNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null) {
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = nextNode.getAndNullValue();
            spConsumerNode(nextNode);
            return nextValue;
        }
        else if (currConsumerNode != lvProducerNode()) {
            // spin, we are no longer wait free
            while((nextNode = currConsumerNode.lvNext()) == null);
            // got the next node...
            
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = nextNode.getAndNullValue();
            consumerNode = nextNode;
            return nextValue;
        }
        return null;
    }

    @Override
    public final E peek() {
        LinkedQueueNode<E> currConsumerNode = consumerNode; // don't load twice, it's alright
        LinkedQueueNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null) {
            return nextNode.lpValue();
        }
        else if (currConsumerNode != lvProducerNode()) {
            // spin, we are no longer wait free
            while((nextNode = currConsumerNode.lvNext()) == null);
            // got the next node...
            return nextNode.lpValue();
        }
        return null;
    }
    @SuppressWarnings("unchecked")
    protected final LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal) {
        return (LinkedQueueNode<E>) UNSAFE.getAndSetObject(this, P_NODE_OFFSET, newVal);
    }
}