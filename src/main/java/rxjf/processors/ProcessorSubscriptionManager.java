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

package rxjf.processors;

import static rxjf.internal.UnsafeAccess.*;

import java.util.Objects;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.*;

/**
 * 
 */
@SuppressWarnings({ "rawtypes" })
final class ProcessorSubscriptionManager<T> {
    volatile Subscriber[] subscribers;
    static final long SUBSCRIBERS = addressOf(ProcessorSubscriptionManager.class, "subscribers");
    static final Subscriber[] EMPTY = new Subscriber[0];
    static final Subscriber[] TERMINATED = new Subscriber[0];
    volatile Object value;
    static final long VALUE = addressOf(ProcessorSubscriptionManager.class, "value");
    boolean active;
    final NotificationLite<T> nl = NotificationLite.instance();
    public ProcessorSubscriptionManager() {
        active = true;
        UNSAFE.putOrderedObject(this, SUBSCRIBERS, EMPTY);
    }
    @SuppressWarnings("unchecked")
    public Subscriber<T>[] subscribers() {
        return subscribers;
    }
    public boolean add(Subscriber<? super T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        for (;;) {
            Subscriber[] curr = subscribers;
            if (curr == TERMINATED) {
                return false;
            }
            int n = curr.length;
            int n2 = n + 1;
            
            Subscriber[] next = new Subscriber[n2];
            System.arraycopy(curr, 0, next, 0, n);
            next[n] = subscriber;
            
            if (UNSAFE.compareAndSwapObject(this, SUBSCRIBERS, curr, next)) {
                return true;
            }
        }
    }
    public boolean remove(Subscriber<? super T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        for (;;) {
            Subscriber[] curr = subscribers;
            if (curr == TERMINATED || curr == EMPTY) {
                return false;
            }
            
            int j = -1;
            int n = curr.length;
            for (int i = 0; i < n; i++) {
                if (Objects.equals(curr[i], subscriber)) {
                    j = i;
                    break;
                }
            }
            if (j < 0) {
                return false;
            }
            
            Subscriber[] next;
            int n2 = n - 1;
            if (n2 == 0) {
                next = EMPTY;
            } else {
                next = new Subscriber[n2];
                System.arraycopy(curr, 0, next, 0, j);
                System.arraycopy(curr, j + 1, next, j, n2 - j);
            }
            if (UNSAFE.compareAndSwapObject(this, SUBSCRIBERS, curr, next)) {
                return true;
            }
        }
    }
    @SuppressWarnings("unchecked")
    public Subscriber<T>[] terminate() {
        active = false;
        Subscriber[] curr = (Subscriber[])UNSAFE.getAndSetObject(this, SUBSCRIBERS, TERMINATED);
        return curr;
    }
    @SuppressWarnings("unchecked")
    public Subscriber<T>[] terminate(Object value) {
        Subscriber[] curr = subscribers;
        if (curr != TERMINATED) {
            active = false;
            lazySet(value);
            curr = (Subscriber[])UNSAFE.getAndSetObject(this, SUBSCRIBERS, TERMINATED);
        }
        return curr;
    }
    public Object get() {
        return value;
    }
    public void set(Object value) {
        this.value = value;
    }
    public void lazySet(Object value) {
        UNSAFE.putOrderedObject(this, VALUE, value);
    }
    
    public static final class ProcessorSubscriber<T> implements Subscriber<T> {
        @Override
        public void onSubscribe(Subscription subscription) {
            // TODO Auto-generated method stub
            
        }
        @Override
        public void onNext(T item) {
            // TODO Auto-generated method stub
            
        }
        @Override
        public void onError(Throwable throwable) {
            // TODO Auto-generated method stub
            
        }
        @Override
        public void onComplete() {
            // TODO Auto-generated method stub
            
        }
    }
}
