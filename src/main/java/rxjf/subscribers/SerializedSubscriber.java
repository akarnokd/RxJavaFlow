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
package rxjf.subscribers;

import java.util.*;
import java.util.function.*;

import rxjf.Flow.Subscriber;
import rxjf.Flow.Subscription;
import rxjf.internal.Conformance;

/**
 *
 */
public final class SerializedSubscriber<T> implements Subscriber<T> {
    final Subscriber<? super T> actual;
    private SerializedSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    public static <T> SerializedSubscriber<T> wrap(Subscriber<T> subscriber) {
        Conformance.subscriberNonNull(subscriber);
        if (subscriber instanceof SerializedSubscriber) {
            return (SerializedSubscriber<T>) subscriber;
        }
        return new SerializedSubscriber<>(subscriber);
    }
    
    static final int TYPE_SUBSCRIPTION = 0;
    static final int TYPE_NEXT = 1;
    static final int TYPE_ERROR = 2;
    static final int TYPE_COMPLETE = 3;
    /** Accessed why synchronizing on this. */
    boolean emitting;
    /** Accessed while emitting = true. */
    Subscription subscription;
    /** Accessed why synchronizing on this. */
    List<Object> queue;
    /** Accessed why synchronizing on this. */
    volatile boolean done;
    boolean accept(Object value) {
        if (value == COMPLETE_TOKEN) {
            done = true;
            actual.onComplete();
            return false;
        }
        Class<?> clazz = value.getClass();
        if (clazz == ErrorToken.class) {
            done = true;
            actual.onError(((ErrorToken)value).error);
            return false;
        } else
        if (clazz == SubscribeToken.class) {
            if (subscription != null) {
                done = true;
                actual.onError(new IllegalStateException("Subscription already set!")); // FIXME reference rule
                return false;
            }
            Subscription s = ((SubscribeToken)value).subscription;
            this.subscription = s;
            actual.onSubscribe(s);
            return true;
        }
        
        @SuppressWarnings("unchecked")
        T t = (T)value;
        actual.onNext(t);
        
        return true;
    }
    
    <U> void handle(Consumer<U> firstEmitter, Function<U, Object> lateEmitter, U value, int mode) {
        if (done) {
            return;
        }
        synchronized (this) {
            if (done) {
                return;
            }
            if (emitting) {
                List<Object> q = queue;
                if (q == null) {
                    q = new ArrayList<>();
                    queue = q;
                }
                if (mode == TYPE_ERROR) {
                    q.clear(); // error cuts ahead
                }
                q.add(lateEmitter.apply(value));
                return;
            }
            emitting = true;
        }
        
        boolean skipFinal = false;
        try {
            
            firstEmitter.accept(value);
            if (mode == TYPE_ERROR || mode == TYPE_COMPLETE) {
                done = true;
                return;
            }
            
            for (;;) {
                List<Object> q;
                synchronized (this) {
                    q = queue;
                    queue = null; // XXX queue clear?
                    if (q == null) {
                        emitting = false;
                        skipFinal = true;
                        break;
                    }
                }
                for (Object o : q) {
                    if (!accept(o)) {
                        break;
                    }
                }
            }
        } finally {
            if (!skipFinal) {
                synchronized (this) {
                    emitting = false;
                }
            }
        }
    }
    @Override
    public void onSubscribe(Subscription subscription) {
        Conformance.subscriptionNonNull(subscription);
        handle(s -> {
            Subscription curr = this.subscription;
            if (!Conformance.onSubscribeOnce(curr, this)) {
                curr.cancel();
                return;
            }
            this.subscription = s;
            this.actual.onSubscribe(s);
        }, SubscribeToken::new, subscription, TYPE_SUBSCRIPTION);
    }
    
    @Override
    public void onNext(T item) {
        Conformance.itemNonNull(item);
        handle(v -> {
            Conformance.subscriptionNonNull(subscription);
            this.actual.onNext(v);
        }, v -> v, item, TYPE_NEXT);
    }
    @Override
    public void onError(Throwable throwable) {
        Conformance.throwableNonNull(throwable);
        handle(v -> {
            Conformance.subscriptionNonNull(subscription);
            actual.onError(v);
        }, ErrorToken::new, throwable, TYPE_ERROR);
    }
    @Override
    public void onComplete() {
        handle(v -> { 
            Conformance.subscriptionNonNull(subscription);
            actual.onComplete(); 
        }, v -> v, null, TYPE_COMPLETE);
    }
    
    static final class ErrorToken {
        final Throwable error;
        ErrorToken(Throwable error) {
            this.error = error;
        }
    }
    static final class SubscribeToken {
        final Subscription subscription;
        SubscribeToken(Subscription subscription) {
            this.subscription = subscription;
        }
    }
    
    static final Object COMPLETE_TOKEN = new Object();
    
    public DisposableSubscriber<T> toDisposable() {
        return DisposableSubscriber.wrap(this);
    }
}
