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

package com.netflix.eureka.registry;

import com.netflix.eureka.registry.AwsDataCenterInfo.AwsDataCenterInfoBuilder;
import com.netflix.eureka.registry.NetworkAddress.ProtocolType;

/**
 * @author Tomasz Bak
 */
public enum SampleAwsDataCenterInfo {

    UsEast1a() {
        @Override
        public AwsDataCenterInfoBuilder builder() {
            return new AwsDataCenterInfoBuilder()
                    .withRegion("US-East-1")
                    .withZone("US-East-1a")
                    .withPlacementGroup("pg-1")
                    .withAmiId("ami-12345678")
                    .withInstanceId("id-12345678")
                    .withInstanceType("m1.large")
                    .withAddresses(new NetworkAddress(ProtocolType.IPv4, false, "192.168.0.1", "us-east-1a-vm.internal"))
                    .withAddresses(new NetworkAddress(ProtocolType.IPv4, true, "10.10.0.1", "us-east-1a-vm.public"));
        }
    },
    UsEast1c() {
        @Override
        public AwsDataCenterInfoBuilder builder() {
            return new AwsDataCenterInfoBuilder()
                    .withRegion("US-East-1")
                    .withZone("US-East-1c")
                    .withPlacementGroup("pg-1")
                    .withAmiId("ami-12345678")
                    .withInstanceId("id-12345678")
                    .withInstanceType("m1.large")
                    .withAddresses(NetworkAddress.privateIPv4("192.168.1.1"), NetworkAddress.publicHostNameIPv4("us-east-1c-vm.test"));
        }
    };


    public abstract AwsDataCenterInfoBuilder builder();

    public AwsDataCenterInfo build() {
        return builder().build();
    }
}
