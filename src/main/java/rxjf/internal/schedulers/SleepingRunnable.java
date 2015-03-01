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
package rxjf.internal.schedulers;

import rxjf.schedulers.Scheduler;


/* package */final class SleepingRunnable implements Runnable {
    private final Runnable underlying;
    private final Scheduler.Worker innerScheduler;
    private final long execTime;

    public SleepingRunnable(Runnable underlying, Scheduler.Worker scheduler, long execTime) {
        this.underlying = underlying;
        this.innerScheduler = scheduler;
        this.execTime = execTime;
    }

    @Override
    public void run() {
        if (innerScheduler.isDisposed()) {
            return;
        }
        if (execTime > innerScheduler.now()) {
            long delay = execTime - innerScheduler.now();
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }

        // after waking up check the subscription
        if (innerScheduler.isDisposed()) {
            return;
        }
        underlying.run();
    }
}
