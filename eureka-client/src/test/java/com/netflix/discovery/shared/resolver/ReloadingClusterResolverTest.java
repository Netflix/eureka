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

package com.netflix.discovery.shared.resolver;

import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ReloadingClusterResolverTest {

    private final InjectableFactory factory = new InjectableFactory();

    private ReloadingClusterResolver resolver;

    @After
    public void tearDown() throws Exception {
        if (resolver != null) {
            resolver.shutdown();
        }
    }

    @Test(timeout = 30000)
    public void testDataAreReloadedPeriodically() throws Exception {
        List<EurekaEndpoint> firstEndpointList = SampleCluster.UsEast1a.build();
        factory.setEndpoints(firstEndpointList);

        // First endpoint list is loaded eagerly
        resolver = new ReloadingClusterResolver(factory, 1);
        assertThat(resolver.getClusterEndpoints(), is(equalTo(firstEndpointList)));

        // Swap with a different one
        List<EurekaEndpoint> secondEndpointList = SampleCluster.UsEast1b.build();
        factory.setEndpoints(secondEndpointList);

        assertThat(awaitUpdate(resolver, secondEndpointList), is(true));
    }

    @Test(timeout = 30000)
    public void testIdenticalListsDoNotCauseReload() throws Exception {
        List<EurekaEndpoint> firstEndpointList = SampleCluster.UsEast1a.build();
        factory.setEndpoints(firstEndpointList);

        // First endpoint list is loaded eagerly
        resolver = new ReloadingClusterResolver(factory, 1);
        assertThat(resolver.getClusterEndpoints(), is(equalTo(firstEndpointList)));

        // Now inject the same one but in the different order
        List<EurekaEndpoint> snapshot = resolver.getClusterEndpoints();
        factory.setEndpoints(ResolverUtils.randomize(firstEndpointList));
        Thread.sleep(5);

        assertThat(resolver.getClusterEndpoints(), is(equalTo(snapshot)));

        // Now inject different list
        List<EurekaEndpoint> secondEndpointList = SampleCluster.UsEast1b.build();
        factory.setEndpoints(secondEndpointList);

        assertThat(awaitUpdate(resolver, secondEndpointList), is(true));
    }

    private static boolean awaitUpdate(ReloadingClusterResolver resolver, List<EurekaEndpoint> expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5 * 1000;
        do {
            List<EurekaEndpoint> current = resolver.getClusterEndpoints();
            if (ResolverUtils.identical(current, expected)) {
                return true;
            }
            Thread.sleep(1);
        } while (System.currentTimeMillis() < deadline);
        throw new TimeoutException("Endpoint list not reloaded on time");
    }

    static class InjectableFactory implements ClusterResolverFactory {

        private final AtomicReference<List<EurekaEndpoint>> currentEndpointsRef = new AtomicReference<>();

        @Override
        public ClusterResolver createClusterResolver() {
            return new StaticClusterResolver(currentEndpointsRef.get());
        }

        void setEndpoints(List<EurekaEndpoint> endpoints) {
            currentEndpointsRef.set(endpoints);
        }
    }
}