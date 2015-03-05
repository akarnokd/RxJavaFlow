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

import rx.Flow.Subscription;
import rx.*;

/**
 * TODO javadoc
 */
public final class BehaviorSubject<T> extends Subject<T, T> {
    /**
     * Creates a {@link BehaviorSubject} without a default item.
     *
     * @param <T>
     *            the type of item the Subject will emit
     * @return the constructed {@link BehaviorSubject}
     */
    public static <T> BehaviorSubject<T> create() {
        return create(null, false);
    }
    /**
     * Creates a {@link BehaviorSubject} that emits the last item it observed and all subsequent items to each
     * {@link Observer} that subscribes to it.
     * 
     * @param <T>
     *            the type of item the Subject will emit
     * @param defaultValue
     *            the item that will be emitted first to any {@link Observer} as long as the
     *            {@link BehaviorSubject} has not yet observed any items from its source {@code Observable}
     * @return the constructed {@link BehaviorSubject}
     */
    public static <T> BehaviorSubject<T> create(T defaultValue) {
        return create(defaultValue, true);
    }
    private static <T> BehaviorSubject<T> create(T defaultValue, boolean hasDefault) {
        // TODO
        throw new UnsupportedOperationException();
    }
    
    private BehaviorSubject(OnSubscribe<T> onSubscribe) {
        super(onSubscribe);
    }
    
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
    @Override
    public boolean hasThrowable() {
        // TODO Auto-generated method stub
        return false;
    }
    @Override
    public boolean hasComplete() {
        // TODO Auto-generated method stub
        return false;
    }
    @Override
    public Throwable getThrowable() {
        // TODO Auto-generated method stub
        return null;
    }
    @Override
    public boolean hasSubscribers() {
        // TODO Auto-generated method stub
        return false;
    }
}
