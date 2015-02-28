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

import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry for plugin implementations that allows global override and handles the retrieval of correct
 * implementation based on order of precedence:
 * <ol>
 * <li>plugin registered globally via {@code register} methods in this class</li>
 * <li>plugin registered and retrieved using {@link java.lang.System#getProperty(String)} (see get methods for
 * property names)</li>
 * <li>default implementation</li>
 * </ol>
 *
 * @see <a href="https://github.com/ReactiveX/RxJava/wiki/Plugins">RxJava Wiki: Plugins</a>
 */
public class RxJavaFlowPlugins {
    private final static RxJavaFlowPlugins INSTANCE = new RxJavaFlowPlugins();

    private final AtomicReference<RxJavaFlowErrorHandler> errorHandler = new AtomicReference<>();
    private final AtomicReference<RxJavaFlowableExecutionHook> observableExecutionHook = new AtomicReference<>();
    private final AtomicReference<RxJavaFlowSchedulersHook> schedulersHook = new AtomicReference<>();

    /**
     * Retrieves the single {@code RxJavaPlugins} instance.
     *
     * @return the single {@code RxJavaPlugins} instance
     */
    public static RxJavaFlowPlugins getInstance() {
        return INSTANCE;
    }

    /* package accessible for unit tests */RxJavaFlowPlugins() {

    }

    /* package accessible for unit tests */void reset() {
        INSTANCE.errorHandler.set(null);
        INSTANCE.observableExecutionHook.set(null);
        INSTANCE.schedulersHook.set(null);
    }

    static final RxJavaFlowErrorHandler DEFAULT_ERROR_HANDLER = new RxJavaFlowErrorHandler() {
    };

    /**
     * Retrieves an instance of {@link RxJavaFlowErrorHandler} to use based on order of precedence as defined in
     * {@link RxJavaFlowPlugins} class header.
     * <p>
     * Override the default by calling {@link #registerErrorHandler(RxJavaFlowErrorHandler)} or by setting the
     * property {@code rxjava.plugin.RxJavaErrorHandler.implementation} with the full classname to load.
     * 
     * @return {@link RxJavaFlowErrorHandler} implementation to use
     */
    public RxJavaFlowErrorHandler getErrorHandler() {
        if (errorHandler.get() == null) {
            // check for an implementation from System.getProperty first
            Object impl = getPluginImplementationViaProperty(RxJavaFlowErrorHandler.class);
            if (impl == null) {
                // nothing set via properties so initialize with default 
                errorHandler.compareAndSet(null, DEFAULT_ERROR_HANDLER);
                // we don't return from here but call get() again in case of thread-race so the winner will always get returned
            } else {
                // we received an implementation from the system property so use it
                errorHandler.compareAndSet(null, (RxJavaFlowErrorHandler) impl);
            }
        }
        return errorHandler.get();
    }

    /**
     * Registers an {@link RxJavaFlowErrorHandler} implementation as a global override of any injected or default
     * implementations.
     * 
     * @param impl
     *            {@link RxJavaFlowErrorHandler} implementation
     * @throws IllegalStateException
     *             if called more than once or after the default was initialized (if usage occurs before trying
     *             to register)
     */
    public void registerErrorHandler(RxJavaFlowErrorHandler impl) {
        if (!errorHandler.compareAndSet(null, impl)) {
            throw new IllegalStateException("Another strategy was already registered: " + errorHandler.get());
        }
    }

    /**
     * Retrieves the instance of {@link RxJavaFlowableExecutionHook} to use based on order of precedence as
     * defined in {@link RxJavaFlowPlugins} class header.
     * <p>
     * Override the default by calling {@link #registerObservableExecutionHook(RxJavaFlowableExecutionHook)}
     * or by setting the property {@code rxjava.plugin.RxJavaObservableExecutionHook.implementation} with the
     * full classname to load.
     * 
     * @return {@link RxJavaFlowableExecutionHook} implementation to use
     */
    public RxJavaFlowableExecutionHook getObservableExecutionHook() {
        if (observableExecutionHook.get() == null) {
            // check for an implementation from System.getProperty first
            Object impl = getPluginImplementationViaProperty(RxJavaFlowableExecutionHook.class);
            if (impl == null) {
                // nothing set via properties so initialize with default 
                observableExecutionHook.compareAndSet(null, RxJavaFlowableExecutionHookDefault.getInstance());
                // we don't return from here but call get() again in case of thread-race so the winner will always get returned
            } else {
                // we received an implementation from the system property so use it
                observableExecutionHook.compareAndSet(null, (RxJavaFlowableExecutionHook) impl);
            }
        }
        return observableExecutionHook.get();
    }

    /**
     * Register an {@link RxJavaFlowableExecutionHook} implementation as a global override of any injected or
     * default implementations.
     * 
     * @param impl
     *            {@link RxJavaFlowableExecutionHook} implementation
     * @throws IllegalStateException
     *             if called more than once or after the default was initialized (if usage occurs before trying
     *             to register)
     */
    public void registerObservableExecutionHook(RxJavaFlowableExecutionHook impl) {
        if (!observableExecutionHook.compareAndSet(null, impl)) {
            throw new IllegalStateException("Another strategy was already registered: " + observableExecutionHook.get());
        }
    }

    private static Object getPluginImplementationViaProperty(Class<?> pluginClass) {
        String classSimpleName = pluginClass.getSimpleName();
        /*
         * Check system properties for plugin class.
         * <p>
         * This will only happen during system startup thus it's okay to use the synchronized
         * System.getProperties as it will never get called in normal operations.
         */
        String implementingClass = System.getProperty("rxjava.plugin." + classSimpleName + ".implementation");
        if (implementingClass != null) {
            try {
                Class<?> cls = Class.forName(implementingClass);
                // narrow the scope (cast) to the type we're expecting
                cls = cls.asSubclass(pluginClass);
                return cls.newInstance();
            } catch (ClassCastException e) {
                throw new RuntimeException(classSimpleName + " implementation is not an instance of " + classSimpleName + ": " + implementingClass);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(classSimpleName + " implementation class not found: " + implementingClass, e);
            } catch (InstantiationException e) {
                throw new RuntimeException(classSimpleName + " implementation not able to be instantiated: " + implementingClass, e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(classSimpleName + " implementation not able to be accessed: " + implementingClass, e);
            }
        } else {
            return null;
        }
    }

    /**
     * Retrieves the instance of {@link RxJavaFlowSchedulersHook} to use based on order of precedence as defined
     * in the {@link RxJavaFlowPlugins} class header.
     * <p>
     * Override the default by calling {@link #registerSchedulersHook(RxJavaFlowSchedulersHook)} or by setting
     * the property {@code rxjava.plugin.RxJavaSchedulersHook.implementation} with the full classname to
     * load.
     *
     * @return the {@link RxJavaFlowSchedulersHook} implementation in use
     */
    public RxJavaFlowSchedulersHook getSchedulersHook() {
        if (schedulersHook.get() == null) {
            // check for an implementation from System.getProperty first
            Object impl = getPluginImplementationViaProperty(RxJavaFlowSchedulersHook.class);
            if (impl == null) {
                // nothing set via properties so initialize with default
                schedulersHook.compareAndSet(null, RxJavaFlowSchedulersHook.getDefaultInstance());
                // we don't return from here but call get() again in case of thread-race so the winner will always get returned
            } else {
                // we received an implementation from the system property so use it
                schedulersHook.compareAndSet(null, (RxJavaFlowSchedulersHook) impl);
            }
        }
        return schedulersHook.get();
    }

    /**
     * Registers an {@link RxJavaFlowSchedulersHook} implementation as a global override of any injected or
     * default implementations.
     *
     * @param impl
     *            {@link RxJavaFlowSchedulersHook} implementation
     * @throws IllegalStateException
     *             if called more than once or after the default was initialized (if usage occurs before trying
     *             to register)
     */
    public void registerSchedulersHook(RxJavaFlowSchedulersHook impl) {
        if (!schedulersHook.compareAndSet(null, impl)) {
            throw new IllegalStateException("Another strategy was already registered: " + schedulersHook.get());
        }
    }
}
