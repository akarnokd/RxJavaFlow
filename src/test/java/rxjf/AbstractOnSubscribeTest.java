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

package rx.observables;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Test;
import org.mockito.InOrder;

import rx.*;
import rx.Flowable;
import rx.Observer;
import rx.exceptions.TestException;
import rx.functions.*;
import rx.observables.AbstractOnSubscribe.SubscriptionState;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

/**
 * Test if AbstractOnSubscribe adheres to the usual unsubscription and backpressure contracts.
 */
public class AbstractOnSubscribeTest {
    @Test
    public void testJust() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onNext(1);
                state.onComplete();
            }
        };
        
        TestSubscriber<Integer> ts = new TestSubscriber<Integer>();
        
        aos.toFlowable().subscribe(ts);
        
        ts.assertNoErrors();
        ts.assertTerminalEvent();
        ts.assertValues((1));
    }
    @Test
    public void testJustMisbehaving() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onNext(1);
                state.onNext(2);
                state.onComplete();
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        aos.toFlowable().subscribe(o);

        verify(o, never()).onNext(any(Integer.class));
        verify(o, never()).onComplete();
        verify(o).onError(any(IllegalStateException.class));
    }
    @Test
    public void testJustMisbehavingonComplete() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onNext(1);
                state.onComplete();
                state.onComplete();
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        aos.toFlowable().subscribe(o);

        verify(o, never()).onNext(any(Integer.class));
        verify(o, never()).onComplete();
        verify(o).onError(any(IllegalStateException.class));
    }
    @Test
    public void testJustMisbehavingOnError() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onNext(1);
                state.onError(new TestException("Forced failure 1"));
                state.onError(new TestException("Forced failure 2"));
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        aos.toFlowable().subscribe(o);

        verify(o, never()).onNext(any(Integer.class));
        verify(o, never()).onComplete();
        verify(o).onError(any(IllegalStateException.class));
    }
    @Test
    public void testEmpty() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onComplete();
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        aos.toFlowable().subscribe(o);
        
        verify(o, never()).onNext(any(Integer.class));
        verify(o, never()).onError(any(Throwable.class));
        verify(o).onComplete();
    }
    @Test
    public void testNever() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.stop();
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        aos.toFlowable().subscribe(o);
        
        verify(o, never()).onNext(any(Integer.class));
        verify(o, never()).onError(any(Throwable.class));
        verify(o, never()).onComplete();
    }

    @Test
    public void testThrows() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                throw new TestException("Forced failure");
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        aos.toFlowable().subscribe(o);
        
        verify(o, never()).onNext(any(Integer.class));
        verify(o, never()).onComplete();
        verify(o).onError(any(TestException.class));
    }

    @Test
    public void testError() {
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onError(new TestException("Forced failure"));
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        
        aos.toFlowable().subscribe(o);
        
        verify(o, never()).onNext(any(Integer.class));
        verify(o).onError(any(TestException.class));
        verify(o, never()).onComplete();
    }
    @Test
    public void testRange() {
        final int start = 1;
        final int count = 100;
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                long calls = state.calls();
                if (calls <= count) {
                    state.onNext((int)calls + start);
                    if (calls == count) {
                        state.onComplete();
                    }
                }
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        aos.toFlowable().subscribe(o);
        
        verify(o, never()).onError(any(TestException.class));
        for (int i = start; i < start + count; i++) {
            inOrder.verify(o).onNext(i);
        }
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    public void testFromIterable() {
        int n = 100;
        final List<Integer> source = new ArrayList<Integer>();
        for (int i = 0; i < n; i++) {
            source.add(i);
        }
        
        AbstractOnSubscribe<Integer, Iterator<Integer>> aos = new AbstractOnSubscribe<Integer, Iterator<Integer>>() {
            @Override
            protected Iterator<Integer> onSubscribe(
                    Subscriber<? super Integer> subscriber) {
                return source.iterator();
            }
            @Override
            protected void next(SubscriptionState<Integer, Iterator<Integer>> state) {
                Iterator<Integer> it = state.state();
                if (it.hasNext()) {
                    state.onNext(it.next());
                }
                if (!it.hasNext()) {
                    state.onComplete();
                }
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        aos.toFlowable().subscribe(o);
        
        verify(o, never()).onError(any(TestException.class));
        for (int i = 0; i < n; i++) {
            inOrder.verify(o).onNext(i);
        }
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
    
    @Test
    public void testPhased() {
        final int count = 100;
        AbstractOnSubscribe<String, Void> aos = new AbstractOnSubscribe<String, Void>() {
            @Override
            protected void next(SubscriptionState<String, Void> state) {
                long c = state.calls();
                switch (state.phase()) {
                case 0:
                    if (c < count) {
                        state.onNext("Beginning");
                        if (c == count - 1) {
                            state.advancePhase();
                        }
                    }
                    break;
                case 1:
                    state.onNext("Beginning");
                    state.advancePhase();
                    break;
                case 2:
                    state.onNext("Finally");
                    state.onComplete();
                    state.advancePhase();
                    break;
                default:
                    throw new IllegalStateException("Wrong phase: " + state.phase());
                }
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        aos.toFlowable().subscribe(o);
        
        verify(o, never()).onError(any(Throwable.class));
        inOrder.verify(o, times(count + 1)).onNext("Beginning");
        inOrder.verify(o).onNext("Finally");
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    public void testPhasedRetry() {
        final int count = 100;
        AbstractOnSubscribe<String, Void> aos = new AbstractOnSubscribe<String, Void>() {
            int calls;
            int phase;
            @Override
            protected void next(SubscriptionState<String, Void> state) {
                switch (phase) {
                case 0:
                    if (calls++ < count) {
                        state.onNext("Beginning");
                        state.onError(new TestException());
                    } else {
                        phase++;
                    }
                    break;
                case 1:
                    state.onNext("Beginning");
                    phase++;
                    break;
                case 2:
                    state.onNext("Finally");
                    state.onComplete();
                    phase++;
                    break;
                default:
                    throw new IllegalStateException("Wrong phase: " + state.phase());
                }
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        aos.toFlowable().retry(2 * count).subscribe(o);
        
        verify(o, never()).onError(any(Throwable.class));
        inOrder.verify(o, times(count + 1)).onNext("Beginning");
        inOrder.verify(o).onNext("Finally");
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    public void testInfiniteTake() {
        int count = 100;
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onNext((int)state.calls());
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        aos.toFlowable().take(count).subscribe(o);
        
        verify(o, never()).onError(any(Throwable.class));
        for (int i = 0; i < 100; i++) {
            inOrder.verify(o).onNext(i);
        }
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    public void testInfiniteRequestSome() {
        int count = 100;
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                state.onNext((int)state.calls());
            }
        };
        
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);
        
        TestSubscriber<Object> ts = new TestSubscriber<Object>(o) {
            @Override
            public void onStart() {
                requestMore(0); // don't start right away
            }
        };
        
        aos.toFlowable().subscribe(ts);
        
        ts.requestMore(count);
        
        verify(o, never()).onError(any(Throwable.class));
        verify(o, never()).onComplete();
        for (int i = 0; i < count; i++) {
            inOrder.verify(o).onNext(i);
        }
        inOrder.verifyNoMoreInteractions();
    }
    @Test
    public void testIndependentStates() {
        int count = 100;
        final ConcurrentHashMap<Object, Object> states = new ConcurrentHashMap<Object, Object>();
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                states.put(state, state);
                state.stop();
            }
        };
        Flowable<Integer> source = aos.toFlowable();
        for (int i = 0; i < count; i++) {
            source.subscribe();
        }
        
        assertEquals(count, states.size());
    }
    @Test(timeout = 3000)
    public void testSubscribeOn() {
        final int start = 1;
        final int count = 100;
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                long calls = state.calls();
                if (calls <= count) {
                    state.onNext((int)calls + start);
                    if (calls == count) {
                        state.onComplete();
                    }
                }
            }
        };

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);
        InOrder inOrder = inOrder(o);

        TestSubscriber<Object> ts = new TestSubscriber<Object>(o);

        aos.toFlowable().subscribeOn(Schedulers.newThread()).subscribe(ts);
        
        ts.awaitTerminalEvent();
        
        verify(o, never()).onError(any(Throwable.class));
        for (int i = 1; i <= count; i++) {
            inOrder.verify(o).onNext(i);
        }
        inOrder.verify(o).onComplete();
        inOrder.verifyNoMoreInteractions();

    }
    @Test(timeout = 10000)
    public void testObserveOn() {
        final int start = 1;
        final int count = 1000;
        AbstractOnSubscribe<Integer, Void> aos = new AbstractOnSubscribe<Integer, Void>() {
            @Override
            protected void next(SubscriptionState<Integer, Void> state) {
                long calls = state.calls();
                if (calls <= count) {
                    state.onNext((int)calls + start);
                    if (calls == count) {
                        state.onComplete();
                    }
                }
            }
        };

        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        TestSubscriber<Object> ts = new TestSubscriber<Object>(o);

        aos.toFlowable().observeOn(Schedulers.newThread()).subscribe(ts);
        
        ts.awaitTerminalEvent();
        
        verify(o, never()).onError(any(Throwable.class));
        verify(o, times(count + 1)).onNext(any(Integer.class));
        verify(o).onComplete();
        
        for (int i = 0; i < ts.getOnNextEvents().size(); i++) {
            Object object = ts.getOnNextEvents().get(i);
            assertEquals(i + 1, object);
        }
    }
    @Test
    public void testMissingEmission() {
        @SuppressWarnings("unchecked")
        Observer<Object> o = mock(Observer.class);

        Action1<SubscriptionState<Object, Void>> empty = Actions.empty();
        AbstractOnSubscribe.create(empty).toFlowable().subscribe(o);
        
        verify(o, never()).onComplete();
        verify(o, never()).onNext(any(Object.class));
        verify(o).onError(any(IllegalStateException.class));
    }
}
