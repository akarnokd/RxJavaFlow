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
package rx;

import java.util.function.Supplier;

import org.junit.*;

/**
 * Just makes sure the compiler settings are okay.
 */
public class VerifyJDK {
    @Test
    public void testJDK() {
        Supplier<String> s = () -> "Works!";
        
        Assert.assertEquals("Works!", s.get());
    }
}
