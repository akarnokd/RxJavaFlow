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

package rxjf.internal.subscriptions;

import static rxjf.internal.UnsafeAccess.addressOf;
import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;

/**
 * Allows replacing an underlying subscription while keeping
 * track of all undelivered requests and re-requesting the amount
 * from the new subscription.
 */
public final class SubscriptionArbiter implements Subscription {
    volatile Subscription current;
    static final long CURRENT = addressOf(SubscriptionArbiter.class, "current");
    
    volatile long requested;
    static final long REQUESTED = addressOf(SubscriptionArbiter.class, "requested");


    @Override
    public void request(long n) {
        // TODO Auto-generated method stub
        
    }
    @Override
    public void cancel() {
        // TODO Auto-generated method stub
        
    }
    
    public void set(Subscription newSubscription) {
        // TODO Auto-generated method stub
        
    }
    
    public <T> void accept(Subscriber<? super T> subscriber, T item) {
        // TODO Auto-generated method stub
        
    }
}
