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

    public static final String PROPERTY_KEYS_PREFIX = "eureka.ext";

    private final String eurekaClusterName;
    private final InetSocketAddress internalReadServerAddress;
    private final Properties properties;

    protected ExtensionContext(String eurekaClusterName, InetSocketAddress internalReadServerAddress, Properties properties) {
        this.eurekaClusterName = eurekaClusterName;
        this.internalReadServerAddress = internalReadServerAddress;
        this.properties = properties;
    }

    /**
     * Unique name assigned to read or write cluster.
     */
    public String getEurekaClusterName() {
        return eurekaClusterName;
    }

    /**
     * TODO: this should be replaced with internal EurekaClient API connecting us directly to local registry
     */
    public InetSocketAddress getInteralReadServerAddress() {
        return internalReadServerAddress;
    }

    /**
     * TODO: we need to provide mechanism to pass configuration to the extensions in a generic way
     */
    public Properties getProperties() {
        return properties;
    }

    public String getProperty(String name) {
        String value = properties.getProperty(name);
        return value == null ? null : (value = value.trim()).isEmpty() ? null : value;
    }

    public static class ExtensionContextBuilder {

        private String eurekaClusterName;
        private InetSocketAddress internalReadServerAddress;
        private Properties properties;
        private boolean addSystemProperties;

        public ExtensionContextBuilder withEurekaClusterName(String eurekaClusterName) {
            this.eurekaClusterName = eurekaClusterName;
            return this;
        }

        public ExtensionContextBuilder withInternalReadServerAddress(InetSocketAddress internalReadServerAddress) {
            this.internalReadServerAddress = internalReadServerAddress;
            return this;
        }

        public ExtensionContextBuilder withProperties(Properties properties) {
            this.properties = properties;
            return this;
        }

        public ExtensionContextBuilder withSystemProperties(boolean addSystemProperties) {
            this.addSystemProperties = addSystemProperties;
            return this;
        }

        public ExtensionContext build() {
            Properties allProperties = new Properties(properties);
            if (addSystemProperties) {
                for (Object keyObj : System.getProperties().keySet()) {
                    if (keyObj instanceof String) {
                        String key = (String) keyObj;
                        if (key.startsWith(PROPERTY_KEYS_PREFIX)) {
                            allProperties.setProperty(key, System.getProperty(key));
                        }
                    }
                }
            }
            return new ExtensionContext(eurekaClusterName, internalReadServerAddress, allProperties);
        }
    }
}
