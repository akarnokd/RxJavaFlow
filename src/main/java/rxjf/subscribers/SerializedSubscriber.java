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

/**
 *
 */
public final class SerializedSubscriber<T> implements Subscriber<T> {
    final Subscriber<? super T> actual;
    private SerializedSubscriber(Subscriber<? super T> actual) {
        this.actual = actual;
    }
    public static <T> Subscriber<T> wrap(Subscriber<T> subscriber) {
        if (subscriber == null) {
            throw new NullPointerException();
        }
        if (subscriber instanceof SerializedSubscriber) {
            return subscriber;
        }
        return new SerializedSubscriber<>(subscriber);
    }
    
    static final int MODE_REGULAR = 0;
    static final int MODE_ERROR = 1;
    static final int MODE_COMPLETE = 2;
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
                if (mode == MODE_ERROR) {
                    q.clear(); // error cuts ahead
                }
                q.add(lateEmitter.apply(value));
                return;
            }
            emitting = true;
        }
        if (this.subscription != null) {
            done = true;
            actual.onError(new IllegalStateException("Subscription already set!")); // FIXME reference rule
            return;
        }
        
        boolean skipFinal = false;
        try {
            
            firstEmitter.accept(value);
            if (mode != MODE_REGULAR) {
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
        if (subscription == null) {
            throw new NullPointerException();
        }
        handle(s -> {
            this.subscription = subscription;
            this.actual.onSubscribe(subscription);
        }, SubscribeToken::new, subscription, MODE_REGULAR);
    }
    
    @Override
    public void onNext(T item) {
        handle(actual::onNext, v -> v, item, MODE_REGULAR);
    }
    @Override
    public void onError(Throwable throwable) {
        handle(v -> actual.onError(v), ErrorToken::new, null, MODE_ERROR);
    }
    @Override
    public void onComplete() {
        handle(v -> actual.onComplete(), v -> v, null, MODE_COMPLETE);
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
}
