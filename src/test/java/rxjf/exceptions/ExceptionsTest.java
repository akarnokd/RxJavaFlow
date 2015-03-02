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
package rxjf.exceptions;

import static org.junit.Assert.*;

import org.junit.*;

import rxjf.Flowable;
import rxjf.processors.PublishProcessor;
import rxjf.subscribers.AbstractSubscriber;

@Ignore // RS prohibits throwing from onXXX methods
public class ExceptionsTest {

    @Test(expected = OnErrorNotImplementedException.class)
    public void testOnErrorNotImplementedIsThrown() {
        Flowable.just(1, 2, 3).subscribe(t1 -> { throw new RuntimeException("hello"); });
    }

    @Test(expected = StackOverflowError.class)
    public void testStackOverflowIsThrown() {
        final PublishProcessor<Integer> a = PublishProcessor.create();
        final PublishProcessor<Integer> b = PublishProcessor.create();
        a.subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(Integer args) {
                System.out.println(args);
            }
        });
        b.subscribe();
        a.subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(Integer args) {
                b.onNext(args + 1);
            }
        });
        b.subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(Integer args) {
                a.onNext(args + 1);
            }
        });
        a.onNext(1);
    }

    @Test(expected = ThreadDeath.class)
    public void testThreadDeathIsThrown() {
        Flowable.just(1).subscribe(new AbstractSubscriber<Integer>() {

            @Override
            public void onComplete() {

            }

            @Override
            public void onError(Throwable e) {
                e.printStackTrace();
            }

            @Override
            public void onNext(Integer t) {
                throw new ThreadDeath();
            }

        });
    }

    /**
     * https://github.com/ReactiveX/RxJava/issues/969
     */
    @Test
    public void testOnErrorExceptionIsThrown() {
        try {
            Flowable.error(new IllegalArgumentException("original exception")).subscribe(new AbstractSubscriber<Object>() {
                @Override
                public void onComplete() {

                }

                @Override
                public void onError(Throwable e) {
                    throw new IllegalStateException("This should be thrown");
                }

                @Override
                public void onNext(Object o) {

                }
            });
            fail("expecting an exception to be thrown");
        } catch (CompositeException t) {
            assertTrue(t.getExceptions().get(0) instanceof IllegalArgumentException);
            assertTrue(t.getExceptions().get(1) instanceof IllegalStateException);
        }
    }

}
