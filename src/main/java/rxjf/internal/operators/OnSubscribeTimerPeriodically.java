/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rxjf.internal.operators;

import java.util.concurrent.TimeUnit;

import rxjf.Flow.Subscriber;
import rxjf.Flowable.OnSubscribe;
import rxjf.internal.subscriptions.DisposableSubscription;
import rxjf.schedulers.Scheduler;

/**
 * Emit 0L after the initial period and ever increasing number after each period.
 * @see <a href='http://msdn.microsoft.com/en-us/library/system.reactive.linq.observable.timer.aspx'>MSDN Flowable.Timer</a>
 */
public final class OnSubscribeTimerPeriodically implements OnSubscribe<Long> {
    final long initialDelay;
    final long period;
    final TimeUnit unit;
    final Scheduler scheduler;

    public OnSubscribeTimerPeriodically(long initialDelay, long period, TimeUnit unit, Scheduler scheduler) {
        this.initialDelay = initialDelay;
        this.period = period;
        this.unit = unit;
        this.scheduler = scheduler;
    }

    @Override
    public void accept(final Subscriber<? super Long> child) {
        final Scheduler.Worker worker = scheduler.createWorker();
        DisposableSubscription ds = DisposableSubscription.createEmpty(child);
        ds.add(worker);
        
        child.onSubscribe(ds);
        
        worker.schedule(new Runnable() {
            long counter;
            @Override
            public void run() {
                try {
                    child.onNext(counter++);
                } catch (Throwable e) {
                    try {
                        child.onError(e);
                    } finally {
                        ds.dispose();
                    }
                }
            }
            
        }, initialDelay, period, unit);
    }
}
