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

package com.netflix.eureka2.testkit.data.builder;

import java.util.Iterator;

import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress;

/**
 * @author Tomasz Bak
 */
public enum SampleAwsDataCenterInfo {

    UsEast1a() {
        @Override
        public AwsDataCenterInfo.Builder builder() {
            return new AwsDataCenterInfo.Builder()
                    .withRegion("US-East-1")
                    .withZone("US-East-1a")
                    .withPlacementGroup("pg-1")
                    .withAmiId("ami-12345678")
                    .withInstanceId("id-12345678")
                    .withInstanceType("m1.large")
                    .withPrivateHostName("us-east-1a-vm.internal")
                    .withPrivateIPv4("192.168.0.1")
                    .withPublicHostName("us-east-1a-vm.public")
                    .withPublicIPv4("11.11.0.1")
                    .withEth0mac("mac:address")
                    .withAccountId("myAccount");
        }
    },
    UsEast1aVpc() {
        @Override
        public AwsDataCenterInfo.Builder builder() {
            return new AwsDataCenterInfo.Builder()
                    .withRegion("US-East-1")
                    .withZone("US-East-1a")
                    .withPlacementGroup("pg-1")
                    .withAmiId("ami-12345678")
                    .withInstanceId("id-12345678")
                    .withInstanceType("m1.large")
                    .withPrivateHostName("us-east-1a-vm.internal")
                    .withPrivateIPv4("192.168.0.1")
                    .withPublicHostName("us-east-1a-vm.public")
                    .withPublicIPv4("11.11.0.1")
                    .withEth0mac("mac:address")
                    .withAccountId("myAccount")
                    .withVpcId("someVpcId");
        }
    },
    UsEast1c() {
        @Override
        public AwsDataCenterInfo.Builder builder() {
            return new AwsDataCenterInfo.Builder()
                    .withRegion("US-East-1")
                    .withZone("US-East-1c")
                    .withPlacementGroup("pg-1")
                    .withAmiId("ami-12345678")
                    .withInstanceId("id-12345678")
                    .withInstanceType("m1.large")
                    .withPrivateHostName("us-east-1c-vm.test")
                    .withPrivateIPv4("192.168.1.1")
                    .withEth0mac("mac:address")
                    .withAccountId("myAccount");
        }
    };

    public abstract AwsDataCenterInfo.Builder builder();

    public AwsDataCenterInfo build() {
        return builder().build();
    }

    public static Iterator<AwsDataCenterInfo> collectionOf(String baseName, final AwsDataCenterInfo template) {
        final Iterator<NetworkAddress> publicAddresses = SampleNetworkAddress.collectionOfIPv4("20", baseName + ".public.net", "public");
        final Iterator<NetworkAddress> privateAddresses = SampleNetworkAddress.collectionOfIPv4("10", baseName + ".private.internal", "private");

        return new Iterator<AwsDataCenterInfo>() {

            private int instanceId;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public AwsDataCenterInfo next() {
                NetworkAddress publicAddress = publicAddresses.next();
                NetworkAddress privateAddress = privateAddresses.next();
                return new AwsDataCenterInfo.Builder()
                        .withAwsDataCenter(template)
                        .withInstanceId(String.format("id-%08d", ++instanceId))
                        .withPublicHostName(publicAddress.getHostName())
                        .withPublicIPv4(publicAddress.getIpAddress())
                        .withPrivateHostName(privateAddress.getHostName())
                        .withPrivateIPv4(privateAddress.getIpAddress())
                        .build();
            }

            @Override
            public void remove() {
                throw new IllegalStateException("Operation not supported");
            }
        };
    }
}
