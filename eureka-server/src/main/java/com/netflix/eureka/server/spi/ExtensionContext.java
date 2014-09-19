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

import javax.inject.Singleton;
import java.net.InetSocketAddress;
import java.util.Properties;
import java.util.ServiceLoader;

/**
 * Eureka extensions discovery is based on {@link ServiceLoader} mechanism.
 * For seamless configuration this class providesd basic information, that should
 * be sufficient for the extension bootstrapping.
 *
 * @author Tomasz Bak
 */
@Singleton
public class ExtensionContext {

    /**
     * Unique name assigned to read or write cluster.
     */
    public String eurekaClusterName() {
        return null;
    }

    /**
     * TODO: this should be replaced with internal EurekaClient API connecting us directly to local registry
     */
    public InetSocketAddress interalReadServerAddress() {
        return null;
    }

    /**
     * TODO: we need to provide mechanism to pass configuration to the extensions in a generic way
     */
    public Properties properties() {
        return null;
    }
}
