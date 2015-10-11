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

package com.netflix.discovery.shared.resolver.aws;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Tomasz Bak
 */
public enum SampleCluster {

    UsEast1a() {
        @Override
        public SampleClusterBuilder builder() {
            return new SampleClusterBuilder("us-east-1", "us-east-1a", "10.10.10.");
        }
    },
    UsEast1b() {
        @Override
        public SampleClusterBuilder builder() {
            return new SampleClusterBuilder("us-east-1", "us-east-1b", "10.10.20.");
        }
    },
    UsEast1c() {
        @Override
        public SampleClusterBuilder builder() {
            return new SampleClusterBuilder("us-east-1", "us-east-1c", "10.10.30.");
        }
    };

    public abstract SampleClusterBuilder builder();

    public List<AwsEndpoint> build() {
        return builder().build();
    }

    public static List<AwsEndpoint> merge(SampleCluster... sampleClusters) {
        List<AwsEndpoint> endpoints = new ArrayList<>();
        for (SampleCluster cluster : sampleClusters) {
            endpoints.addAll(cluster.build());
        }
        return endpoints;
    }

    public static class SampleClusterBuilder {
        private final String region;
        private final String zone;
        private final String networkPrefix;
        private int serverPoolSize = 2;

        public SampleClusterBuilder(String region, String zone, String networkPrefix) {
            this.region = region;
            this.zone = zone;
            this.networkPrefix = networkPrefix;
        }

        public SampleClusterBuilder withServerPool(int serverPoolSize) {
            this.serverPoolSize = serverPoolSize;
            return this;
        }

        public List<AwsEndpoint> build() {
            List<AwsEndpoint> endpoints = new ArrayList<>();
            for (int i = 0; i < serverPoolSize; i++) {
                String hostName = networkPrefix + i;
                endpoints.add(new AwsEndpoint(
                        hostName,
                        80,
                        false,
                        "/eureka/v2",
                        region,
                        zone
                ));
            }
            return endpoints;
        }
    }
}
