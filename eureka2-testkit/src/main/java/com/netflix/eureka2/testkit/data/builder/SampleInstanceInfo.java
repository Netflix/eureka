package com.netflix.eureka2.testkit.data.builder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.model.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Builder;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.instance.NetworkAddress;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.utils.ExtCollections;

/**
 * @author Tomasz Bak
 */
public enum SampleInstanceInfo {

    WebServer(),
    Backend(),
    ZuulServer(),
    DiscoveryServer(),
    CliServer(),
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

    public Builder builder() {
        return templateFor(this.name());
    }

    public InstanceInfo build() {
        return builder().build();
    }

    public Iterator<InstanceInfo> cluster() {
        return collectionOf(name(), build());
    }

    public List<InstanceInfo> clusterOf(int clusterSize) {
        List<InstanceInfo> cluster = new ArrayList<>();
        Iterator<InstanceInfo> clusterIt = cluster();
        for (int i = 0; i < clusterSize; i++) {
            cluster.add(clusterIt.next());
        }
        return cluster;
    }

    protected Builder templateFor(String name) {
        HashSet<String> healthCheckUrls = new HashSet<>();
        healthCheckUrls.add("http://eureka/healthCheck/" + name);
        healthCheckUrls.add("https://eureka/healthCheck/" + name);
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

    /**
     * return an interator that creates new InstanceInfos based on the template, where the template will define the
     * appName and vipAddress for all the IsntanceInfos
     */
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
                        .withInstanceId(String.format("i-%s-%08d", baseName, cidx))
                        .withPublicHostName(publicAddress.getHostName())
                        .withPublicIPv4(publicAddress.getIpAddress())
                        .withPrivateHostName(privateAddress.getHostName())
                        .withPrivateIPv4(privateAddress.getIpAddress())
                        .build();
                return new InstanceInfo.Builder()
                        .withId("id#" + name)
                        .withApp(template.getApp())
                        .withAppGroup(template.getAppGroup())
                        .withAsg(template.getAsg())
                        .withHealthCheckUrls(template.getHealthCheckUrls())
                        .withHomePageUrl(template.getHomePageUrl())
                        .withPorts(template.getPorts())
                        .withSecureVipAddress(template.getSecureVipAddress())
                        .withStatus(template.getStatus())
                        .withStatusPageUrl(template.getStatusPageUrl())
                        .withVipAddress(template.getVipAddress())
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
