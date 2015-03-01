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

package rxjf.schedulers;

import static org.junit.Assert.assertTrue;

import java.lang.management.*;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;

import org.junit.Test;

import rxjf.Flowable;
import rxjf.internal.schedulers.NewThreadWorker;

public class CachedThreadSchedulerTest extends AbstractSchedulerConcurrencyTests {

    @Override
    protected Scheduler getScheduler() {
        return Schedulers.io();
    }

    /**
     * IO scheduler defaults to using CachedThreadScheduler
     */
    @Test
    public final void testIOScheduler() {

        Flowable<Integer> o1 = Flowable.just(1, 2, 3, 4, 5);
        Flowable<Integer> o2 = Flowable.just(6, 7, 8, 9, 10);
        Flowable<String> o = Flowable.merge(o1, o2).map(t -> {
            assertTrue(Thread.currentThread().getName().startsWith("RxCachedThreadScheduler"));
            return "Value_" + t + "_Thread_" + Thread.currentThread().getName();
        });

        o.subscribeOn(Schedulers.io()).toBlocking().forEach(t -> {
            System.out.println("t: " + t);
        });
    }

    @Test
    public final void testUnhandledErrorIsDeliveredToThreadHandler() throws InterruptedException {
        SchedulerTests.testUnhandledErrorIsDeliveredToThreadHandler(getScheduler());
    }

    @Test
    public final void testHandledErrorIsNotDeliveredToThreadHandler() throws InterruptedException {
        SchedulerTests.testHandledErrorIsNotDeliveredToThreadHandler(getScheduler());
    }
    
    @Test(timeout = 30000)
    public void testCancelledTaskRetention() throws InterruptedException {
        System.out.println("Wait before GC");
        Thread.sleep(1000);
        
        System.out.println("GC");
        System.gc();
        
        Thread.sleep(1000);

        
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage memHeap = memoryMXBean.getHeapMemoryUsage();
        long initial = memHeap.getUsed();
        
        System.out.printf("Starting: %.3f MB%n", initial / 1024.0 / 1024.0);
        
        Scheduler.Worker w = Schedulers.io().createWorker();
        for (int i = 0; i < 750000; i++) {
            if (i % 50000 == 0) {
                System.out.println("  -> still scheduling: " + i);
            }
            w.schedule(() -> { }, 1, TimeUnit.DAYS);
        }
        
        memHeap = memoryMXBean.getHeapMemoryUsage();
        long after = memHeap.getUsed();
        System.out.printf("Peak: %.3f MB%n", after / 1024.0 / 1024.0);
        
        w.dispose();
        
        System.out.println("Wait before second GC");
        Thread.sleep(NewThreadWorker.PURGE_FREQUENCY + 2000);
        
        System.out.println("Second GC");
        System.gc();
        
        Thread.sleep(1000);
        
        memHeap = memoryMXBean.getHeapMemoryUsage();
        long finish = memHeap.getUsed();
        System.out.printf("After: %.3f MB%n", finish / 1024.0 / 1024.0);
        
        if (finish > initial * 5) {
            Assert.fail(String.format("Tasks retained: %.3f -> %.3f -> %.3f", initial / 1024 / 1024.0, after / 1024 / 1024.0, finish / 1024 / 1024d));
        }
    }

}