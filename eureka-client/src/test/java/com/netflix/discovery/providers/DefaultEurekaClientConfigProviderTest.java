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

package com.netflix.discovery.providers;

import java.util.List;

import com.google.inject.Injector;
import com.netflix.config.ConfigurationManager;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.EurekaNamespace;
import com.netflix.governator.guice.BootstrapBinder;
import com.netflix.governator.guice.BootstrapModule;
import com.netflix.governator.guice.LifecycleInjector;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class DefaultEurekaClientConfigProviderTest {

    private static final String SERVICE_URI = "http://my.eureka.server:8080/";

    @Test
    public void testNameSpaceInjection() throws Exception {
        ConfigurationManager.getConfigInstance().setProperty("testnamespace.serviceUrl.default", SERVICE_URI);

        Injector injector = LifecycleInjector.builder()
                .withBootstrapModule(new BootstrapModule() {
                    @Override
                    public void configure(BootstrapBinder binder) {
                        binder.bind(String.class).annotatedWith(EurekaNamespace.class).toInstance("testnamespace.");
                    }
                })
                .build()
                .createInjector();

        DefaultEurekaClientConfig clientConfig = injector.getInstance(DefaultEurekaClientConfig.class);

        List<String> serviceUrls = clientConfig.getEurekaServerServiceUrls("default");
        assertThat(serviceUrls.get(0), is(equalTo(SERVICE_URI)));
    }
}