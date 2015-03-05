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

package rx.subjects;

import org.junit.*;

import rx.subscribers.TestSubscriber;

public class SubjectSubscriberManagerTest {
    @Test
    public void simpleAddRemove() {
        SubjectSubscriberManager<Integer> psm = new SubjectSubscriberManager<>();
        TestSubscriber<Integer> ts = new TestSubscriber<>();
        
        psm.add(ts);
        
        Assert.assertTrue(psm.remove(ts));
        Assert.assertFalse(psm.remove(ts));
    }
    @Test
    public void simpleMultiAddRemoveFirst() {
        SubjectSubscriberManager<Integer> psm = new SubjectSubscriberManager<>();
        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        TestSubscriber<Integer> ts3 = new TestSubscriber<>();
        
        Assert.assertTrue(psm.add(ts1));
        Assert.assertTrue(psm.add(ts2));
        Assert.assertTrue(psm.add(ts3));
        
        Assert.assertTrue(psm.remove(ts1));
        Assert.assertArrayEquals(new Object[] { ts2, ts3 }, psm.subscribers());
    }
    @Test
    public void simpleMultiAddRemoveMiddle() {
        SubjectSubscriberManager<Integer> psm = new SubjectSubscriberManager<>();
        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        TestSubscriber<Integer> ts3 = new TestSubscriber<>();
        
        Assert.assertTrue(psm.add(ts1));
        Assert.assertTrue(psm.add(ts2));
        Assert.assertTrue(psm.add(ts3));
        
        Assert.assertTrue(psm.remove(ts2));
        Assert.assertArrayEquals(new Object[] { ts1, ts3 }, psm.subscribers());
    }
    @Test
    public void simpleMultiAddRemoveLast() {
        SubjectSubscriberManager<Integer> psm = new SubjectSubscriberManager<>();
        TestSubscriber<Integer> ts1 = new TestSubscriber<>();
        TestSubscriber<Integer> ts2 = new TestSubscriber<>();
        TestSubscriber<Integer> ts3 = new TestSubscriber<>();
        
        Assert.assertTrue(psm.add(ts1));
        Assert.assertTrue(psm.add(ts2));
        Assert.assertTrue(psm.add(ts3));
        
        Assert.assertTrue(psm.remove(ts3));
        Assert.assertArrayEquals(new Object[] { ts1, ts2 }, psm.subscribers());
    }
}
