/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.server.spi;

import com.google.inject.AbstractModule;
import com.netflix.eureka.server.spi.ExtensionLoader.StandardExtension;

/**
 * All extensions must provide modules derived from this class to
 * be discoverable.
 *
 * @author Tomasz Bak
 */
public abstract class ExtAbstractModule extends AbstractModule {

    /**
     * Module will be added to the container if it is runnable. It is up to the
     * module implementation to decide what are the conditions for that.
     * As a minimum modules shall verify that its configuration is complete.
     * This allows to failover to default service implementation, if available.
     * @param extensionContext
     */
    public boolean isRunnable(ExtensionContext extensionContext) {
        return true;
    }

    /**
     * Return {@link StandardExtension} which this module implements. For non
     * standard extensions return {@link StandardExtension#Undefined}.
     */
    public StandardExtension standardExtension() {
        return StandardExtension.Undefined;
    }
}
