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
package rxjf.schedulers;

import java.util.concurrent.TimeUnit;

import rxjf.cancellables.*;

/**
 *
 */
public interface Scheduler {
    /**
     * 
     */
    interface Worker extends Cancellable {
        
        Cancellable schedule(Runnable task);
        
        Cancellable schedule(Runnable task, long delay, TimeUnit unit);
        
        default long now() {
            return System.currentTimeMillis();
        }
        
        default Cancellable schedule(Runnable task, long initialDelay, long period, TimeUnit unit) {
            final long periodInNanos = unit.toNanos(period);
            final long startInNanos = TimeUnit.MILLISECONDS.toNanos(now()) + unit.toNanos(initialDelay);

            final MultipleAssignmentCancellable mas = new MultipleAssignmentCancellable();
            final Runnable recursiveRunnable = new Runnable() {
                long count = 0;
                @Override
                public void run() {
                    if (!mas.isCancelled()) {
                        task.run();
                        long nextTick = startInNanos + (++count * periodInNanos);
                        mas.set(schedule(this, nextTick - TimeUnit.MILLISECONDS.toNanos(now()), TimeUnit.NANOSECONDS));
                    }
                }
            };
            MultipleAssignmentCancellable s = new MultipleAssignmentCancellable();
            // Should call `mas.set` before `schedule`, or the new Subscription may replace the old one.
            mas.set(s);
            s.set(schedule(recursiveRunnable, initialDelay, unit));
            return mas;

        }
    }
    
    Worker createWorker();
    
    default long now() {
        return System.currentTimeMillis();
    }
}
