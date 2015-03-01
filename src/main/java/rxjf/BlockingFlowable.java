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

package rxjf;

import java.util.Iterator;

/**
 * 
 */
public final class BlockingFlowable<T> {
    final Flowable<? extends T> source;
    public BlockingFlowable(Flowable<? extends T> source) {
        this.source = source;
    }
    public T single() {
        // TODO
        throw new UnsupportedOperationException();
    }
    public T singleOrDefault(T defaultValue) {
        // TODO
        throw new UnsupportedOperationException();
    }
    public T first() {
        // TODO
        throw new UnsupportedOperationException();
    }
    public T last() {
        // TODO
        throw new UnsupportedOperationException();
    }
    public T lastOrDefault() {
        // TODO
        throw new UnsupportedOperationException();
    }
    public Iterator<T> getIterator() {
        // TODO
        throw new UnsupportedOperationException();
    }
}
