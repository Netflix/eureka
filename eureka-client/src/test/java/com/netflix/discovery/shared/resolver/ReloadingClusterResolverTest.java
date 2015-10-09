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

import com.netflix.discovery.shared.resolver.aws.AwsEndpoint;
import com.netflix.discovery.shared.resolver.aws.SampleCluster;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class ReloadingClusterResolverTest {

    private final InjectableFactory factory = new InjectableFactory();

    private ReloadingClusterResolver<AwsEndpoint> resolver;

    @Test(timeout = 30000)
    public void testDataAreReloadedPeriodically() throws Exception {
        List<AwsEndpoint> firstEndpointList = SampleCluster.UsEast1a.build();
        factory.setEndpoints(firstEndpointList);

        // First endpoint list is loaded eagerly
        resolver = new ReloadingClusterResolver<>(factory, 1);
        assertThat(resolver.getClusterEndpoints(), is(equalTo(firstEndpointList)));

        // Swap with a different one
        List<AwsEndpoint> secondEndpointList = SampleCluster.UsEast1b.build();
        factory.setEndpoints(secondEndpointList);

        assertThat(awaitUpdate(resolver, secondEndpointList), is(true));
    }

    @Test(timeout = 30000)
    public void testIdenticalListsDoNotCauseReload() throws Exception {
        List<AwsEndpoint> firstEndpointList = SampleCluster.UsEast1a.build();
        factory.setEndpoints(firstEndpointList);

        // First endpoint list is loaded eagerly
        resolver = new ReloadingClusterResolver(factory, 1);
        assertThat(resolver.getClusterEndpoints(), is(equalTo(firstEndpointList)));

        // Now inject the same one but in the different order
        List<AwsEndpoint> snapshot = resolver.getClusterEndpoints();
        factory.setEndpoints(ResolverUtils.randomize(firstEndpointList));
        Thread.sleep(5);

        assertThat(resolver.getClusterEndpoints(), is(equalTo(snapshot)));

        // Now inject different list
        List<AwsEndpoint> secondEndpointList = SampleCluster.UsEast1b.build();
        factory.setEndpoints(secondEndpointList);

        assertThat(awaitUpdate(resolver, secondEndpointList), is(true));
    }

    private static boolean awaitUpdate(ReloadingClusterResolver<AwsEndpoint> resolver, List<AwsEndpoint> expected) throws Exception {
        long deadline = System.currentTimeMillis() + 5 * 1000;
        do {
            List<AwsEndpoint> current = resolver.getClusterEndpoints();
            if (ResolverUtils.identical(current, expected)) {
                return true;
            }
            Thread.sleep(1);
        } while (System.currentTimeMillis() < deadline);
        throw new TimeoutException("Endpoint list not reloaded on time");
    }

    static class InjectableFactory implements ClusterResolverFactory<AwsEndpoint> {

        private final AtomicReference<List<AwsEndpoint>> currentEndpointsRef = new AtomicReference<>();

        @Override
        public ClusterResolver<AwsEndpoint> createClusterResolver() {
            return new StaticClusterResolver<>("regionA", currentEndpointsRef.get());
        }

        void setEndpoints(List<AwsEndpoint> endpoints) {
            currentEndpointsRef.set(endpoints);
        }
    }
}