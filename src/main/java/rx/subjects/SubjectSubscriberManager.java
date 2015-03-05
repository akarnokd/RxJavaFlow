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

import static rx.internal.UnsafeAccess.*;
import rx.Flow.Subscriber;
import rx.internal.*;

/**
 * Manages the Subscribers and current/terminal value for the Processors.
 */
final class SubjectSubscriberManager<T> extends AbstractArrayManager<Subscriber<? super T>> {
    volatile Object value;
    static final long VALUE = addressOf(SubjectSubscriberManager.class, "value");
    
    boolean active;
    final NotificationLite<T> nl = NotificationLite.instance();
    @SuppressWarnings("unchecked")
    public SubjectSubscriberManager() {
        super(i -> new Subscriber[i]);
        active = true;
    }
    public Subscriber<? super T>[] subscribers() {
        return array();
    }
    public Subscriber<? super T>[] terminate() {
        active = false;
        return getAndTerminate();
    }
    public Subscriber<? super T>[] terminate(Object value) {
        Subscriber<? super T>[] curr = array();
        if (curr != terminated) {
            active = false;
            set(value);
            curr = terminate();
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
}
