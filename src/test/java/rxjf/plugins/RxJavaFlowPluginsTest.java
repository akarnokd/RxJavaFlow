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
package rxjf.plugins;

import static org.junit.Assert.*;

import org.junit.*;

import rxjf.Flowable;
import rxjf.subscribers.AbstractSubscriber;

public class RxJavaFlowPluginsTest {

    @Before
    public void resetBefore() {
        RxJavaFlowPlugins.getInstance().reset();
    }

    @After
    public void resetAfter() {
        RxJavaFlowPlugins.getInstance().reset();
    }

    @Test
    public void testErrorHandlerDefaultImpl() {
        RxJavaFlowErrorHandler impl = new RxJavaFlowPlugins().getErrorHandler();
        assertSame(RxJavaFlowPlugins.DEFAULT_ERROR_HANDLER, impl);
    }

    @Test
    public void testErrorHandlerViaRegisterMethod() {
        RxJavaFlowPlugins p = new RxJavaFlowPlugins();
        p.registerErrorHandler(new RxJavaErrorHandlerTestImpl());
        RxJavaFlowErrorHandler impl = p.getErrorHandler();
        assertTrue(impl instanceof RxJavaErrorHandlerTestImpl);
    }

    @Test
    public void testErrorHandlerViaProperty() {
        String key = "rxjava.plugin." + RxJavaFlowErrorHandler.class.getSimpleName() + ".implementation";
        try {
            RxJavaFlowPlugins p = new RxJavaFlowPlugins();
            String fullClass = getFullClassNameForTestClass(RxJavaErrorHandlerTestImpl.class);
            System.setProperty(key, fullClass);
            RxJavaFlowErrorHandler impl = p.getErrorHandler();
            System.out.println(impl);
            assertTrue(impl instanceof RxJavaErrorHandlerTestImpl);
        } finally {
            System.clearProperty(key);
        }
    }

    // inside test so it is stripped from Javadocs
    public static class RxJavaErrorHandlerTestImpl extends RxJavaFlowErrorHandler {

        private volatile Throwable e;
        private volatile int count = 0;

        @Override
        public void handleError(Throwable e) {
            e.printStackTrace();
            this.e = e;
            count++;
        }

    }

    @Test
    public void testFlowableExecutionHookDefaultImpl() {
        RxJavaFlowPlugins p = new RxJavaFlowPlugins();
        RxJavaFlowableExecutionHook impl = p.getFlowableExecutionHook();
        assertTrue(impl instanceof RxJavaFlowableExecutionHookDefault);
    }

    @Test
    public void testFlowableExecutionHookViaRegisterMethod() {
        RxJavaFlowPlugins p = new RxJavaFlowPlugins();
        p.registerFlowableExecutionHook(new RxJavaFlowableExecutionHookTestImpl());
        RxJavaFlowableExecutionHook impl = p.getFlowableExecutionHook();
        assertTrue(impl instanceof RxJavaFlowableExecutionHookTestImpl);
    }

    @Test
    public void testFlowableExecutionHookViaProperty() {
        try {
            RxJavaFlowPlugins p = new RxJavaFlowPlugins();
            String fullClass = getFullClassNameForTestClass(RxJavaFlowableExecutionHookTestImpl.class);
            System.setProperty("rxjava.plugin.RxJavaFlowableExecutionHook.implementation", fullClass);
            RxJavaFlowableExecutionHook impl = p.getFlowableExecutionHook();
            assertTrue(impl instanceof RxJavaFlowableExecutionHookTestImpl);
        } finally {
            System.clearProperty("rxjava.plugin.RxJavaFlowableExecutionHook.implementation");
        }
    }

    @Ignore // RS prohibits throwing in onError
    @Test
    public void testOnErrorWhenImplementedViaSubscribe() {
        RxJavaErrorHandlerTestImpl errorHandler = new RxJavaErrorHandlerTestImpl();
        RxJavaFlowPlugins.getInstance().registerErrorHandler(errorHandler);

        RuntimeException re = new RuntimeException("test onError");
        Flowable.error(re).subscribe(new AbstractSubscriber<Object>() {

            @Override
            public void onComplete() {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(Object args) {
            }
        });
        assertEquals(re, errorHandler.e);
        assertEquals(1, errorHandler.count);
    }

    @Ignore // RS prohibits throwing from methods
    @Test
    public void testOnErrorWhenNotImplemented() {
        RxJavaErrorHandlerTestImpl errorHandler = new RxJavaErrorHandlerTestImpl();
        RxJavaFlowPlugins.getInstance().registerErrorHandler(errorHandler);

        RuntimeException re = new RuntimeException("test onError");
        try {
            Flowable.error(re).subscribe();
            fail("should fail");
        } catch (Throwable e) {
            // ignore as we expect it to throw
        }
        assertEquals(re, errorHandler.e);
        assertEquals(1, errorHandler.count);
    }

    // inside test so it is stripped from Javadocs
    public static class RxJavaFlowableExecutionHookTestImpl extends RxJavaFlowableExecutionHook {
        // just use defaults
    }

    private static String getFullClassNameForTestClass(Class<?> cls) {
        return RxJavaFlowPlugins.class.getPackage().getName() + "." + RxJavaFlowPluginsTest.class.getSimpleName() + "$" + cls.getSimpleName();
    }
}
