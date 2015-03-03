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

import java.util.function.LongConsumer;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.Flowable.Operator;
import rxjf.subscribers.AbstractSubscriber;

/**
 * This operator modifies an {@link rx.Flowable} so a given action is invoked when the {@link rx.Flowable.Producer} receives a request.
 * 
 * @param <T>
 *            The type of the elements in the {@link rx.Flowable} that this operator modifies
 */
public class OperatorDoOnRequest<T> implements Operator<T, T> {

    private final LongConsumer request;

    public OperatorDoOnRequest(LongConsumer request) {
        this.request = request;
    }

    @Override
    public Subscriber<? super T> apply(final Subscriber<? super T> child) {
        return new AbstractSubscriber<T>() {
            @Override
            protected void onSubscribe() {
                child.onSubscribe(new Subscription() {
                    @Override
                    public void request(long n) {
                        request.accept(n);
                        subscription.request(n);
                    }
                    @Override
                    public void cancel() {
                        subscription.cancel();
                    }
                });
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
        };
    }

}