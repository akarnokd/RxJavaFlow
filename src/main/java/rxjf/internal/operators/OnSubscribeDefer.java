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
package rxjf.internal.operators;

import rx.Flowable;
import rx.Flowable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Supplier;

/**
 * Do not create the Flowable until an Observer subscribes; create a fresh Flowable on each
 * subscription.
 * <p>
 * <img width="640" src="https://github.com/ReactiveX/RxJava/wiki/images/rx-operators/defer.png" alt="">
 * <p>
 * Pass defer an Flowable factory function (a function that generates Flowables), and defer will
 * return an Flowable that will call this function to generate its Flowable sequence afresh
 * each time a new Observer subscribes.
 */
public final class OnSubscribeDefer<T> implements OnSubscribe<T> {
    final Supplier<? extends Flowable<? extends T>> observableFactory;

    public OnSubscribeDefer(Supplier<? extends Flowable<? extends T>> observableFactory) {
        this.observableFactory = observableFactory;
    }

    @Override
    public void call(Subscriber<? super T> s) {
        Flowable<? extends T> o;
        try {
            o = observableFactory.call();
        } catch (Throwable t) {
            s.onError(t);
            return;
        }
        o.unsafeSubscribe(s);
    }
    
}
