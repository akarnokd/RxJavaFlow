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
package rxjf;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;

import rxjf.CovarianceTest.HorrorMovie;
import rxjf.CovarianceTest.Media;
import rxjf.CovarianceTest.Movie;
import rxjf.internal.subscriptions.AbstractSubscription;

public class MergeTests {

    /**
     * This won't compile if super/extends isn't done correctly on generics
     */
    @Test
    public void testCovarianceOfMerge() {
        Flowable<HorrorMovie> horrors = Flowable.just(new HorrorMovie());
        Flowable<Flowable<HorrorMovie>> metaHorrors = Flowable.just(horrors);
        Flowable.<Media> merge(metaHorrors);
    }

    @Test
    public void testMergeCovariance() {
        Flowable<Media> o1 = Flowable.<Media> just(new HorrorMovie(), new Movie());
        Flowable<Media> o2 = Flowable.just(new Media(), new HorrorMovie());

        Flowable<Flowable<Media>> os = Flowable.just(o1, o2);

        List<Media> values = Flowable.merge(os).toList().toBlocking().single();
        
        assertEquals(4, values.size());
    }

    @Test
    public void testMergeCovariance2() {
        Flowable<Media> o1 = Flowable.just(new HorrorMovie(), new Movie(), new Media());
        Flowable<Media> o2 = Flowable.just(new Media(), new HorrorMovie());

        Flowable<Flowable<Media>> os = Flowable.just(o1, o2);

        List<Media> values = Flowable.merge(os).toList().toBlocking().single();

        assertEquals(5, values.size());
    }

    @Test
    public void testMergeCovariance3() {
        Flowable<Movie> o1 = Flowable.just(new HorrorMovie(), new Movie());
        Flowable<Media> o2 = Flowable.just(new Media(), new HorrorMovie());

        List<Media> values = Flowable.merge(o1, o2).toList().toBlocking().single();

        assertTrue(values.get(0) instanceof HorrorMovie);
        assertTrue(values.get(1) instanceof Movie);
        assertTrue(values.get(2) != null);
        assertTrue(values.get(3) instanceof HorrorMovie);
    }

    @Test
    public void testMergeCovariance4() {

        Flowable<Movie> o1 = Flowable.create(o -> {
            AbstractSubscription.setEmptyOn(o);
            o.onNext(new HorrorMovie());
            o.onNext(new Movie());
            //                o.onNext(new Media()); // correctly doesn't compile
            o.onComplete();
        });

        Flowable<Media> o2 = Flowable.just(new Media(), new HorrorMovie());

        List<Media> values = Flowable.merge(o1, o2).toList().toBlocking().single();

        assertTrue(values.get(0) instanceof HorrorMovie);
        assertTrue(values.get(1) instanceof Movie);
        assertTrue(values.get(2) != null);
        assertTrue(values.get(3) instanceof HorrorMovie);
    }

}
