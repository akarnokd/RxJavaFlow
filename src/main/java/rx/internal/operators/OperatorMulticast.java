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
package rx.internal.operators;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

import rx.Flow.Subscriber;
import rx.Flow.Subscription;
import rx.Observable;
import rx.disposables.Disposable;
import rx.observables.ConnectableObservable;
import rx.subjects.Subject;
import rx.subscribers.AbstractDisposableSubscriber;

/**
 * Shares a single subscription to a source through a Subject.
 * 
 * @param <T>
 *            the source value type
 * @param <R>
 *            the result value type
 */
public final class OperatorMulticast<T, R> extends ConnectableObservable<R> {
    final Observable<? extends T> source;
    final Object guard;
    final Supplier<? extends Subject<? super T, ? extends R>> subjectFactory;
    final AtomicReference<Subject<? super T, ? extends R>> connectedSubject;
    final List<Subscriber<? super R>> waitingForConnect;

    /** Guarded by guard. */
    private AbstractDisposableSubscriber<T> subscriber;
    // wraps subscription above for unsubscription using guard
    private Disposable guardedSubscription;

    public OperatorMulticast(Observable<? extends T> source, final Supplier<? extends Subject<? super T, ? extends R>> subjectFactory) {
        this(new Object(), new AtomicReference<Subject<? super T, ? extends R>>(), new ArrayList<Subscriber<? super R>>(), source, subjectFactory);
    }

    private OperatorMulticast(final Object guard, 
            final AtomicReference<Subject<? super T, ? extends R>> connectedSubject, 
            final List<Subscriber<? super R>> waitingForConnect, 
            Observable<? extends T> source, 
            final Supplier<? extends Subject<? super T, ? extends R>> subjectFactory) {
        super(s -> {
            synchronized (guard) {
                Subject<? super T, ? extends R> subject = connectedSubject.get();
                if (subject == null) {
                    // not connected yet, so register
                    waitingForConnect.add(s);
                } else {
                    subject.unsafeSubscribe(s);
                }
            }
        });
        this.guard = guard;
        this.connectedSubject = connectedSubject;
        this.waitingForConnect = waitingForConnect;
        this.source = source;
        this.subjectFactory = subjectFactory;
    }

    @Override
    public void connect(Consumer<? super Disposable> connection) {
        // each time we connect we create a new Subject and Subscription

        // subscription is the state of whether we are connected or not
        synchronized (guard) {
            if (subscriber != null) {
                // already connected
                connection.accept(guardedSubscription);
                return;
            } else {
                // we aren't connected, so let's create a new Subject and connect
                final Subject<? super T, ? extends R> subject = subjectFactory.get();
                
                // create new Subscriber that will pass-thru to the subject we just created
                // we do this since it is also a Subscription whereas the Subject is not
                subscriber = new AbstractDisposableSubscriber<T>() {
                    @Override
                    protected void internalOnSubscribe(Subscription subscription) {
                        subject.onSubscribe(subscription);
                    }
                    @Override
                    public void internalOnComplete() {
                        subject.onComplete();
                    }

                    @Override
                    public void internalOnError(Throwable e) {
                        subject.onError(e);
                    }

                    @Override
                    public void internalOnNext(T args) {
                        subject.onNext(args);
                    }
                };
                final AtomicReference<Disposable> gs = new AtomicReference<>();
                gs.set(Disposable.from(() -> {
                    Disposable s;
                    synchronized (guard) {
                        if (guardedSubscription != gs.get()) {
                            return;
                        }
                        s = subscriber;
                        subscriber = null;
                        guardedSubscription = null;
                        connectedSubject.set(null);
                    }
                    if (s != null) {
                        s.dispose();
                    }
                }));
                guardedSubscription = gs.get();
                
                // register any subscribers that are waiting with this new subject
                @SuppressWarnings("unchecked")
                Observable<R> p = (Observable<R>)subject;
                for(Subscriber<? super R> s : waitingForConnect) {
                    p.unsafeSubscribe(s);
                }
                
                // clear the waiting list as any new ones that come in after leaving this synchronized block will go direct to the Subject
                waitingForConnect.clear();
                // record the Subject so OnSubscribe can see it
                connectedSubject.set(subject);
            }
            
        }

        // in the lock above we determined we should subscribe, do it now outside the lock
        // register a subscription that will shut this down
        connection.accept(guardedSubscription);

        // now that everything is hooked up let's subscribe
        // as long as the subscription is not null (which can happen if already unsubscribed)
        Subscriber<T> sub; 
        synchronized (guard) {
            sub = subscriber;
        }
        if (sub != null) {
            source.subscribe(sub);
        }
    }
}