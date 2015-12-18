/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.internal.util;

import java.util.*;

import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import com.netflix.eureka2.spi.model.ModelProvider;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Auto-discovery of transport/codec/model provider implementations, using Java service loader. The following rules are applied:
 * <ul>
 * <li>check if provider system property is set. If so, return model instance of this providerType</li>
 * <li>otherwise, if there is single other provider than the standard available, use it</li>
 * <li>otherwise, if there is more then one none standard provider available, use first in alphabetic order</li>
 * <li>otherwise, if the only available provider is the standard one, use it</li>
 * </ul>
 */
public final class ExtLoader {

    private static final Logger logger = LoggerFactory.getLogger(ExtLoader.class);

    public static final String MODEL_PROVIDER_PARAMETER = "eureka2.model.provider";
    public static final String CODEC_FACTORY_PARAMETER = "eureka2.codec.factory";
    public static final String CLIENT_TRANSPORT_FACTORY_PARAMETER = "eureka2.transportClient.factory";
    public static final String SERVER_TRANSPORT_FACTORY_PARAMETER = "eureka2.transportServer.factory";

    /**
     * Standard model is an optional component on classpath, so we cannot use the {@link Class} object directly.
     */
    private static final String STD_MODEL_PROVIDER = "com.netflix.eureka2.StdModelProvider";
    private static final String STD_CODEC_FACTORY = "com.netflix.eureka2.codec.jackson.JacksonEurekaCodecFactory";
    private static final String STD_CLIENT_TRANSPORT_FACTORY = "com.netflix.eureka2.?";
    private static final String STD_SERVER_TRANSPORT_FACTORY = "com.netflix.eureka2.?";

    private static volatile ModelProvider defaultModelProvider;
    private static volatile EurekaCodecFactory defaultCodecFactory;
    private static volatile EurekaClientTransportFactory defaultClientTransportFactory;
    private static volatile EurekaServerTransportFactory defaultServerTransportFactory;

    private static final Object lock = new Object();

    private ExtLoader() {
    }

    public static ModelProvider resolveDefaultModel() {
        if (defaultModelProvider == null) {
            synchronized (lock) {
                if (defaultModelProvider == null) {
                    defaultModelProvider = new ExtProviderResolver<>(ModelProvider.class, STD_MODEL_PROVIDER, MODEL_PROVIDER_PARAMETER).resolve();
                    logger.info("Using {} model provider", defaultModelProvider.getClass().getName());
                }
            }
        }
        return defaultModelProvider;
    }

    public static EurekaCodecFactory resolveDefaultCodecFactory() {
        if (defaultCodecFactory == null) {
            synchronized (lock) {
                if (defaultCodecFactory == null) {
                    defaultCodecFactory = new ExtProviderResolver<>(EurekaCodecFactory.class, STD_CODEC_FACTORY, CODEC_FACTORY_PARAMETER).resolve();
                    logger.info("Using {} codec factory", defaultCodecFactory.getClass().getName());
                }
            }
        }
        return defaultCodecFactory;
    }

    public static EurekaClientTransportFactory resolveDefaultClientTransportFactory() {
        if (defaultClientTransportFactory == null) {
            synchronized (lock) {
                if (defaultClientTransportFactory == null) {
                    defaultClientTransportFactory = new ExtProviderResolver<>(EurekaClientTransportFactory.class, STD_CLIENT_TRANSPORT_FACTORY, CLIENT_TRANSPORT_FACTORY_PARAMETER).resolve();
                    logger.info("Using {} client transport factory", defaultClientTransportFactory.getClass().getName());
                }
            }
        }
        return defaultClientTransportFactory;
    }

    public static EurekaServerTransportFactory resolveDefaultServerTransportFactory() {
        if (defaultServerTransportFactory == null) {
            synchronized (lock) {
                if (defaultServerTransportFactory == null) {
                    defaultServerTransportFactory = new ExtProviderResolver<>(EurekaServerTransportFactory.class, STD_SERVER_TRANSPORT_FACTORY, SERVER_TRANSPORT_FACTORY_PARAMETER).resolve();
                    logger.info("Using {} server transport factory", defaultServerTransportFactory.getClass().getName());
                }
            }
        }
        return defaultServerTransportFactory;
    }

    static class ExtProviderResolver<E> {

        private final Class<E> providerType;
        private final String systemProperty;

        private final Map<Class, E> providers = new HashMap<>();
        private E stdExtProvider;

        ExtProviderResolver(Class<E> providerType, String stdProviderType, String systemProperty) {
            this.providerType = providerType;
            this.systemProperty = systemProperty;
            ServiceLoader<E> serviceLoader = ServiceLoader.load(providerType);
            serviceLoader.forEach(provider -> {
                if (provider.getClass().getName().equals(stdProviderType)) {
                    stdExtProvider = provider;
                } else {
                    providers.put(provider.getClass(), provider);
                }
            });
        }

        public E resolve() {
            if (providers.isEmpty() && stdExtProvider == null) {
                logger.error("No implementation of {} interface auto-discovered", ModelProvider.class);
                throw new IllegalStateException("Auto-discovery of model implementation failure");
            }
            if (System.getProperty(MODEL_PROVIDER_PARAMETER) != null) {
                return loadFromSystemProperty();
            }
            if (providers.isEmpty()) {
                return stdExtProvider;
            }
            if (providers.size() == 1) {
                return providers.values().iterator().next();
            }

            // We have more than 1 non-standard implementations. Pick first in the alphabetic order.
            List<Class> names = new ArrayList<>(providers.keySet());
            Collections.sort(names, (c1, c2) -> c1.getName().compareTo(c2.getName()));
            return providers.get(names.get(0));
        }

        private E loadFromSystemProperty() {
            String className = System.getProperty(systemProperty);
            Class type;
            try {
                type = Class.forName(className);
            } catch (ClassNotFoundException e) {
                logger.error("Auto-discovery of {} implementation failure; {} property value {} is not a valid class name", providerType.getSimpleName(), systemProperty, className);
                throw new IllegalStateException("Auto-discovery of model implementation failure");
            }
            if (!ModelProvider.class.isAssignableFrom(type)) {
                logger.error("Provider configured via system property {}={} does not implement {} interface", systemProperty, className, providerType);
                throw new IllegalStateException("Auto-discovery of model implementation failure");
            }
            try {
                return (E) type.newInstance();
            } catch (Exception e) {
                logger.error("Cannot instantiate {} of type {}, configured via system property {}", providerType.getSimpleName(), className, systemProperty, className);
                logger.error("Model provider instantiation error", e);
                throw new IllegalStateException("Auto-discovery of model implementation failure");
            }
        }
    }
}
