/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.resources;


import com.netflix.eureka.Version;

/**
 * A thread-scoped value that holds the "current {@link com.netflix.eureka.Version}" for the
 * request.
 *
 * <p>This is not intended as a general mechanism for passing data.
 * Rather it is here to support those cases where someplace deep in
 * a library we need to know about the context of the request that
 * initially triggered the current request.</p>
 *
 * @author Karthik Ranganathan, Greg Kim
 */
public final class CurrentRequestVersion {

    private static final ThreadLocal<Version> CURRENT_REQ_VERSION =
            new ThreadLocal<>();

    private CurrentRequestVersion() {
    }

    /**
     * Gets the current {@link Version}
     * Will return null if no current version has been set.
     */
    public static Version get() {
        return CURRENT_REQ_VERSION.get();
    }

    /**
     * Sets the current {@link Version}.
     *
     * Use {@link #remove()} as soon as the version is no longer required
     * in order to purge the ThreadLocal used for storing it.
     */
    public static void set(Version version) {
        CURRENT_REQ_VERSION.set(version);
    }

    /**
     * Clears the {@link ThreadLocal} used to store the version.
     */
    public static void remove() {
        CURRENT_REQ_VERSION.remove();
    }

}
