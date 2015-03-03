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
package rx.util;

import org.junit.Test;

import rx.Flowable;

public class AssertFlowableTest {

    @Test
    public void testPassNotNull() {
        AssertFlowable.assertFlowableEqualsBlocking("foo", Flowable.just(1, 2), Flowable.just(1, 2));
    }

    @Test
    public void testPassNull() {
        AssertFlowable.assertFlowableEqualsBlocking("foo", null, null);
    }

    @Test(expected = RuntimeException.class)
    public void testFailNotNull() {
        AssertFlowable.assertFlowableEqualsBlocking("foo", Flowable.just(1, 2), Flowable.just(1));
    }

    @Test(expected = RuntimeException.class)
    public void testFailNull() {
        AssertFlowable.assertFlowableEqualsBlocking("foo", Flowable.just(1, 2), null);
    }
}
