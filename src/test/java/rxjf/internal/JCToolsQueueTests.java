    /**
     * Copyright 2014 Netflix, Inc.
     * 
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

import static org.junit.Assert.*;

import org.junit.Test;

import rxjf.Flow;

public class JCToolsQueueTests {
    @Test
    public void testMpmcOfferUpToCapacity() {
        int n = Flow.defaultBufferSize();
        MpmcArrayQueue<Integer> queue = new MpmcArrayQueue<>(n);
        for (int i = 0; i < n; i++) {
            assertTrue(queue.offer(i));
        }
        assertFalse(queue.offer(n));
    }
    @Test
    public void testSpscOfferUpToCapacity() {
        int n = Flow.defaultBufferSize();
        SpscArrayQueue<Integer> queue = new SpscArrayQueue<>(n);
        for (int i = 0; i < n; i++) {
            assertTrue(queue.offer(i));
        }
        assertFalse(queue.offer(n));
    }
    @Test
    public void testSpmcOfferUpToCapacity() {
        int n = Flow.defaultBufferSize();
        SpmcArrayQueue<Integer> queue = new SpmcArrayQueue<>(n);
        for (int i = 0; i < n; i++) {
            assertTrue(queue.offer(i));
        }
        assertFalse(queue.offer(n));
    }
}
