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

import java.util.*;
import java.util.function.*;

import org.junit.Test;

import rx.Observable.Transformer;
import rx.subscribers.TestSubscriber;

import static org.junit.Assert.*;

/**
 * Test super/extends of generics.
 * 
 * See https://github.com/Netflix/RxJava/pull/331
 */
public class CovarianceTest {

    /**
     * This won't compile if super/extends isn't done correctly on generics
     */
    @Test
    public void testCovarianceOfFrom() {
        Observable.<Movie> just(new HorrorMovie());
        Observable.<Movie> from(new ArrayList<HorrorMovie>());
        // Observable.<HorrorMovie>from(new Movie()); // may not compile
    }

    @Test
    public void testSortedList() {
        BiFunction<Media, Media, Integer> SORT_FUNCTION = (t1, t2) -> 1;

        // this one would work without the covariance generics
        Observable<Media> o = Observable.just(new Movie(), new TVSeason(), new Album());
        o.toSortedList(SORT_FUNCTION);

        // this one would NOT work without the covariance generics
        Observable<Movie> o2 = Observable.just(new Movie(), new ActionMovie(), new HorrorMovie());
        o2.toSortedList(SORT_FUNCTION);
    }

    @Test
    public void testGroupByCompose() {
        Observable<Movie> movies = Observable.just(new HorrorMovie(), new ActionMovie(), new Movie());
        TestSubscriber<String> ts = new TestSubscriber<>();
        movies.groupBy(m -> m.getClass())
        .flatMap(g -> {
                return g.compose(m -> m.concatWith(Observable.just(new ActionMovie())))
                .map(Object::toString);
        }).subscribe(ts);
        
        ts.assertTerminalEvent();
        ts.assertNoErrors();
        //        System.out.println(ts.getOnNextEvents());
        ts.assertValueCount(6);
    }

    @Test
    public void testCovarianceOfCompose() {
        Observable<HorrorMovie> movie = Observable.just(new HorrorMovie());
        Observable<Movie> movie2 = movie.compose(t1 -> Observable.just(new Movie()));
        assertNotNull(movie2);
    }
    
    @Test
    public void testCovarianceOfCompose2() {
        Observable<Movie> movie = Observable.<Movie> just(new HorrorMovie());
        Observable<HorrorMovie> movie2 = movie.compose(t1 -> Observable.just(new HorrorMovie()));
        assertNotNull(movie2);
    }

    @Test
    public void testCovarianceOfCompose3() {
        Observable<Movie> movie = Observable.<Movie>just(new HorrorMovie());
        Observable<HorrorMovie> movie2 = movie.compose(t-> 
                Observable.just(new HorrorMovie()).map(horrorMovie -> horrorMovie));
        assertNotNull(movie2);
    }

    @Test
    public void testCovarianceOfCompose4() {
        Observable<HorrorMovie> movie = Observable.just(new HorrorMovie());
        Observable<HorrorMovie> movie2 = movie.compose(t1 -> t1.map(horrorMovie -> horrorMovie));
        
        assertNotNull(movie2);
    }
    
    @Test
    public void testComposeWithDeltaLogic() {
        List<Movie> list1 = Arrays.asList(new Movie(), new HorrorMovie(), new ActionMovie());
        List<Movie> list2 = Arrays.asList(new ActionMovie(), new Movie(), new HorrorMovie(), new ActionMovie());
        Observable<List<Movie>> movies = Observable.just(list1, list2);
        movies.compose(deltaTransformer);
    }

    static Function<List<List<Movie>>, Observable<Movie>> calculateDelta = listOfLists -> {
            if (listOfLists.size() == 1) {
                return Observable.from(listOfLists.get(0));
            } else {
                // diff the two
                List<Movie> newList = listOfLists.get(1);
                List<Movie> oldList = new ArrayList<>(listOfLists.get(0));

                Set<Movie> delta = new LinkedHashSet<>();
                delta.addAll(newList);
                // remove all that match in old
                delta.removeAll(oldList);

                // filter oldList to those that aren't in the newList
                oldList.removeAll(newList);

                // for all left in the oldList we'll create DROP events
                oldList.forEach(old -> delta.add(new Movie()));

                return Observable.from(delta);
        }
    };

    static Transformer<List<Movie>, Movie> deltaTransformer = movieList ->
    movieList
        .startWith(new ArrayList<Movie>())
        .buffer(2, 1)
        .skip(1)
        .flatMap(calculateDelta)
    ;

    /*
     * Most tests are moved into their applicable classes such as [Operator]Tests.java
     */

    static class Media {
    }

    static class Movie extends Media {
    }

    static class HorrorMovie extends Movie {
    }

    static class ActionMovie extends Movie {
    }

    static class Album extends Media {
    }

    static class TVSeason extends Media {
    }

    static class Rating {
    }

    static class CoolRating extends Rating {
    }

    static class Result {
    }

    static class ExtendedResult extends Result {
    }
}
