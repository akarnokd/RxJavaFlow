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
package rx;

import org.junit.Test;

import rx.EventStream.Event;
import rx.functions.Action1;
import rx.functions.Function;
import rx.observables.GroupedFlowable;

public class GroupByTests {

    @Test
    public void testTakeUnsubscribesOnGroupBy() {
        Flowable.merge(
                EventStream.getEventStream("HTTP-ClusterA", 50),
                EventStream.getEventStream("HTTP-ClusterB", 20))
                // group by type (2 clusters)
                .groupBy(new Function<Event, String>() {

                    @Override
                    public String call(Event event) {
                        return event.type;
                    }

                }).take(1)
                .toBlocking().forEach(new Action1<GroupedFlowable<String, Event>>() {

                    @Override
                    public void call(GroupedFlowable<String, Event> g) {
                        System.out.println(g);
                    }

                });

        System.out.println("**** finished");
    }

    @Test
    public void testTakeUnsubscribesOnFlatMapOfGroupBy() {
        Flowable.merge(
                EventStream.getEventStream("HTTP-ClusterA", 50),
                EventStream.getEventStream("HTTP-ClusterB", 20))
                // group by type (2 clusters)
                .groupBy(new Function<Event, String>() {

                    @Override
                    public String call(Event event) {
                        return event.type;
                    }

                })
                .flatMap(new Function<GroupedFlowable<String, Event>, Flowable<String>>() {

                    @Override
                    public Flowable<String> call(GroupedFlowable<String, Event> g) {
                        return g.map(new Function<Event, String>() {

                            @Override
                            public String call(Event event) {
                                return event.instanceId + " - " + event.values.get("count200");
                            }
                        });
                    }

                })
                .take(20)
                .toBlocking().forEach(new Action1<String>() {

                    @Override
                    public void call(String v) {
                        System.out.println(v);
                    }

                });

        System.out.println("**** finished");
    }
}
