package com.netflix.eureka2.testkit.data.builder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.utils.ExtCollections;

/**
 * @author Tomasz Bak
 */
public enum SampleInstanceInfo {

    ZuulServer() {
        @Override
        public Builder builder() {
            return templateFor(this.name());
        }
    },

    DiscoveryServer() {
        @Override
        public Builder builder() {
            return templateFor(this.name());
        }
    },

    CliServer() {
        @Override
        public Builder builder() {
            return templateFor(this.name());
        }
    },
    EurekaWriteServer() {
        @Override
        public Builder builder() {
            return eurekaWriteTemplate(1);
        }
    },
    EurekaReadServer() {
        @Override
        public Builder builder() {
            Builder builder = templateFor(this.name());
            builder.withPorts(ExtCollections.asSet(
                    SampleServicePort.EurekaDiscoveryPort.build()
            ));
            return builder;
        }
    };

    public abstract Builder builder();

    public InstanceInfo build() {
        return builder().build();
    }

    protected Builder templateFor(String name) {
        HashSet<String> healthCheckUrls = new HashSet<>();
        healthCheckUrls.add("http://eureka/healthCheck/" + name + "1");
        healthCheckUrls.add("http://eureka/healthCheck/" + name + "2");
        HashSet<Integer> ports = new HashSet<>();
        ports.add(80);
        ports.add(8080);
        HashSet<Integer> securePorts = new HashSet<>();
        securePorts.add(443);
        securePorts.add(8443);
        return new Builder()
                .withId("id#" + name + "_" + UUID.randomUUID().toString())
                .withApp("app#" + name)
                .withAppGroup("group#" + name)
                .withAsg("asg#" + name)
                .withHealthCheckUrls(healthCheckUrls)
                .withHomePageUrl("http://eureka/home/" + name)
                .withPorts(ExtCollections.asSet(new ServicePort(7200, false), new ServicePort(7210, true)))
                .withSecureVipAddress("vipSecure#" + name)
                .withStatus(Status.UP)
                .withStatusPageUrl("http://eureka/status/" + name)
                .withVipAddress("vip#" + name)
                .withMetaData("optionA", "valueA")
                .withMetaData("optionB", "valueB")
                .withDataCenterInfo(SampleAwsDataCenterInfo.UsEast1a.build());
    }

    protected Builder eurekaWriteTemplate(int idx) {
        Builder builder = templateFor(this.name() + '#' + idx);
        builder.withPorts(ExtCollections.asSet(
                SampleServicePort.EurekaRegistrationPort.build(),
                SampleServicePort.EurekaDiscoveryPort.build(),
                SampleServicePort.EurekaReplicationPort.build()
        ));

        return builder;
    }

    public static Iterator<InstanceInfo> collectionOf(final String baseName, final InstanceInfo template) {
        final AwsDataCenterInfo templateDataCenter = (AwsDataCenterInfo) template.getDataCenterInfo();
        final AtomicInteger idx = new AtomicInteger();
        final Iterator<NetworkAddress> publicAddresses = SampleNetworkAddress.collectionOfIPv4("20.20", baseName + ".public.net", null);
        final Iterator<NetworkAddress> privateAddresses = SampleNetworkAddress.collectionOfIPv4("192.168", baseName + ".private.internal", null);
        return new Iterator<InstanceInfo>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public InstanceInfo next() {
                int cidx = idx.incrementAndGet();
                String name = baseName + '_' + cidx;
                NetworkAddress publicAddress = publicAddresses.next();
                NetworkAddress privateAddress = privateAddresses.next();
                DataCenterInfo dataCenter = new AwsDataCenterInfo.Builder()
                        .withAwsDataCenter(templateDataCenter)
                        .withPublicHostName(publicAddress.getHostName())
                        .withPublicIPv4(publicAddress.getIpAddress())
                        .withPrivateHostName(privateAddress.getHostName())
                        .withPrivateIPv4(privateAddress.getIpAddress())
                        .build();
                return new InstanceInfo.Builder()
                        .withId("id#" + name)
                        .withApp("app#" + baseName)
                        .withAppGroup("group#" + baseName)
                        .withAsg("asg#" + baseName)
                        .withHealthCheckUrls(template.getHealthCheckUrls())
                        .withHomePageUrl(template.getHomePageUrl())
                        .withPorts(template.getPorts())
                        .withSecureVipAddress("vipSecure#" + name)
                        .withStatus(template.getStatus())
                        .withStatusPageUrl(template.getStatusPageUrl())
                        .withVipAddress("vip#" + baseName)
                        .withMetaData(template.getMetaData())
                        .withDataCenterInfo(dataCenter)
                        .build();
            }

            @Override
            public void remove() {
                throw new IllegalStateException("Operation not supported");
            }
        };
    }
}
