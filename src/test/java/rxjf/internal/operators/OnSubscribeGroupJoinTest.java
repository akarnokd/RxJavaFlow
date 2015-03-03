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
package rx.internal.operators;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import rx.Flowable;
import rx.Observer;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Function;
import rx.functions.BiFunction;
import rx.subjects.PublishSubject;

public class OnSubscribeGroupJoinTest {
    @Mock
    Observer<Object> observer;

    BiFunction<Integer, Integer, Integer> add = new BiFunction<Integer, Integer, Integer>() {
        @Override
        public Integer call(Integer t1, Integer t2) {
            return t1 + t2;
        }
    };

    <T> Function<Integer, Flowable<T>> just(final Flowable<T> observable) {
        return new Function<Integer, Flowable<T>>() {
            @Override
            public Flowable<T> call(Integer t1) {
                return observable;
            }
        };
    }

    <T, R> Function<T, Flowable<R>> just2(final Flowable<R> observable) {
        return new Function<T, Flowable<R>>() {
            @Override
            public Flowable<R> call(T t1) {
                return observable;
            }
        };
    }

    BiFunction<Integer, Flowable<Integer>, Flowable<Integer>> add2 = new BiFunction<Integer, Flowable<Integer>, Flowable<Integer>>() {
        @Override
        public Flowable<Integer> call(final Integer leftValue, Flowable<Integer> rightValues) {
            return rightValues.map(new Function<Integer, Integer>() {
                @Override
                public Integer call(Integer rightValue) {
                    return add.call(leftValue, rightValue);
                }
            });
        }

    };

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void behaveAsJoin() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        Flowable<Integer> m = Flowable.merge(source1.groupJoin(source2,
                just(Flowable.never()),
                just(Flowable.never()), add2));

        m.subscribe(observer);

        source1.onNext(1);
        source1.onNext(2);
        source1.onNext(4);

        source2.onNext(16);
        source2.onNext(32);
        source2.onNext(64);

        source1.onComplete();
        source2.onComplete();

        verify(observer, times(1)).onNext(17);
        verify(observer, times(1)).onNext(18);
        verify(observer, times(1)).onNext(20);
        verify(observer, times(1)).onNext(33);
        verify(observer, times(1)).onNext(34);
        verify(observer, times(1)).onNext(36);
        verify(observer, times(1)).onNext(65);
        verify(observer, times(1)).onNext(66);
        verify(observer, times(1)).onNext(68);

        verify(observer, times(1)).onComplete(); //Never emitted?
        verify(observer, never()).onError(any(Throwable.class));
    }

    class Person {
        final int id;
        final String name;

        public Person(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    class PersonFruit {
        final int personId;
        final String fruit;

        public PersonFruit(int personId, String fruit) {
            this.personId = personId;
            this.fruit = fruit;
        }
    }

    class PPF {
        final Person person;
        final Flowable<PersonFruit> fruits;

        public PPF(Person person, Flowable<PersonFruit> fruits) {
            this.person = person;
            this.fruits = fruits;
        }
    }

    @Test
    public void normal1() {
        Flowable<Person> source1 = Flowable.from(Arrays.asList(
                new Person(1, "Joe"),
                new Person(2, "Mike"),
                new Person(3, "Charlie")
                ));

        Flowable<PersonFruit> source2 = Flowable.from(Arrays.asList(
                new PersonFruit(1, "Strawberry"),
                new PersonFruit(1, "Apple"),
                new PersonFruit(3, "Peach")
                ));

        Flowable<PPF> q = source1.groupJoin(
                source2,
                just2(Flowable.<Object> never()),
                just2(Flowable.<Object> never()),
                new BiFunction<Person, Flowable<PersonFruit>, PPF>() {
                    @Override
                    public PPF call(Person t1, Flowable<PersonFruit> t2) {
                        return new PPF(t1, t2);
                    }
                });

        q.subscribe(
                new Subscriber<PPF>() {
                    @Override
                    public void onNext(final PPF ppf) {
                        ppf.fruits.filter(new Function<PersonFruit, Boolean>() {
                            @Override
                            public Boolean call(PersonFruit t1) {
                                return ppf.person.id == t1.personId;
                            }
                        }).subscribe(new Action1<PersonFruit>() {
                            @Override
                            public void call(PersonFruit t1) {
                                observer.onNext(Arrays.asList(ppf.person.name, t1.fruit));
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        observer.onError(e);
                    }

                    @Override
                    public void onComplete() {
                        observer.onComplete();
                    }

                }
                );

        verify(observer, times(1)).onNext(Arrays.asList("Joe", "Strawberry"));
        verify(observer, times(1)).onNext(Arrays.asList("Joe", "Apple"));
        verify(observer, times(1)).onNext(Arrays.asList("Charlie", "Peach"));

        verify(observer, times(1)).onComplete();
        verify(observer, never()).onError(any(Throwable.class));
    }

    @Test
    public void leftThrows() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        Flowable<Flowable<Integer>> m = source1.groupJoin(source2,
                just(Flowable.never()),
                just(Flowable.never()), add2);

        m.subscribe(observer);

        source2.onNext(1);
        source1.onError(new RuntimeException("Forced failure"));

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onNext(any());
    }

    @Test
    public void rightThrows() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        Flowable<Flowable<Integer>> m = source1.groupJoin(source2,
                just(Flowable.never()),
                just(Flowable.never()), add2);

        m.subscribe(observer);

        source1.onNext(1);
        source2.onError(new RuntimeException("Forced failure"));

        verify(observer, times(1)).onNext(any(Flowable.class));
        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
    }

    @Test
    public void leftDurationThrows() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        Flowable<Integer> duration1 = Flowable.<Integer> error(new RuntimeException("Forced failure"));

        Flowable<Flowable<Integer>> m = source1.groupJoin(source2,
                just(duration1),
                just(Flowable.never()), add2);
        m.subscribe(observer);

        source1.onNext(1);

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onNext(any());
    }

    @Test
    public void rightDurationThrows() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        Flowable<Integer> duration1 = Flowable.<Integer> error(new RuntimeException("Forced failure"));

        Flowable<Flowable<Integer>> m = source1.groupJoin(source2,
                just(Flowable.never()),
                just(duration1), add2);
        m.subscribe(observer);

        source2.onNext(1);

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onNext(any());
    }

    @Test
    public void leftDurationSelectorThrows() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        Function<Integer, Flowable<Integer>> fail = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Integer t1) {
                throw new RuntimeException("Forced failure");
            }
        };

        Flowable<Flowable<Integer>> m = source1.groupJoin(source2,
                fail,
                just(Flowable.never()), add2);
        m.subscribe(observer);

        source1.onNext(1);

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onNext(any());
    }

    @Test
    public void rightDurationSelectorThrows() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        Function<Integer, Flowable<Integer>> fail = new Function<Integer, Flowable<Integer>>() {
            @Override
            public Flowable<Integer> call(Integer t1) {
                throw new RuntimeException("Forced failure");
            }
        };

        Flowable<Flowable<Integer>> m = source1.groupJoin(source2,
                just(Flowable.never()),
                fail, add2);
        m.subscribe(observer);

        source2.onNext(1);

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onNext(any());
    }

    @Test
    public void resultSelectorThrows() {
        PublishSubject<Integer> source1 = PublishSubject.create();
        PublishSubject<Integer> source2 = PublishSubject.create();

        BiFunction<Integer, Flowable<Integer>, Integer> fail = new BiFunction<Integer, Flowable<Integer>, Integer>() {
            @Override
            public Integer call(Integer t1, Flowable<Integer> t2) {
                throw new RuntimeException("Forced failure");
            }
        };

        Flowable<Integer> m = source1.groupJoin(source2,
                just(Flowable.never()),
                just(Flowable.never()), fail);
        m.subscribe(observer);

        source1.onNext(1);
        source2.onNext(2);

        verify(observer, times(1)).onError(any(Throwable.class));
        verify(observer, never()).onComplete();
        verify(observer, never()).onNext(any());
    }
}