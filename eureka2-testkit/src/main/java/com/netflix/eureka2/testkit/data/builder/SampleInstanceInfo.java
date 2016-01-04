package com.netflix.eureka2.testkit.data.builder;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.model.datacenter.DataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.instance.NetworkAddress;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.utils.ExtCollections;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        public InstanceInfoBuilder builder() {
            return eurekaWriteTemplate(1);
        }
    },
    EurekaReadServer() {
        @Override
        public InstanceInfoBuilder builder() {
            InstanceInfoBuilder builder = templateFor(this.name());
            builder.withPorts(ExtCollections.asSet(
                    SampleServicePort.EurekaServerPort.build()
            ));
            return builder;
        }
    };

    public InstanceInfoBuilder builder() {
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

    protected InstanceInfoBuilder templateFor(String name) {
        InstanceModel model = InstanceModel.getDefaultModel();

        HashSet<String> healthCheckUrls = new HashSet<>();
        healthCheckUrls.add("http://eureka/healthCheck/" + name);
        healthCheckUrls.add("https://eureka/healthCheck/" + name);
        HashSet<Integer> ports = new HashSet<>();
        ports.add(80);
        ports.add(8080);
        HashSet<Integer> securePorts = new HashSet<>();
        securePorts.add(443);
        securePorts.add(8443);

        return model.newInstanceInfo()
                .withId("id#" + name + "_" + UUID.randomUUID().toString())
                .withApp("app#" + name)
                .withAppGroup("group#" + name)
                .withAsg("asg#" + name)
                .withHealthCheckUrls(healthCheckUrls)
                .withHomePageUrl("http://eureka/home/" + name)
                .withPorts(ExtCollections.asSet((ServicePort) model.newServicePort(7200, false), model.newServicePort(7210, true)))
                .withSecureVipAddress("vipSecure#" + name)
                .withStatus(Status.UP)
                .withStatusPageUrl("http://eureka/status/" + name)
                .withVipAddress("vip#" + name)
                .withMetaData("optionA", "valueA")
                .withMetaData("optionB", "valueB")
                .withDataCenterInfo(SampleAwsDataCenterInfo.UsEast1a.build());
    }

    protected InstanceInfoBuilder eurekaWriteTemplate(int idx) {
        InstanceInfoBuilder builder = templateFor(this.name() + '#' + idx);
        builder.withPorts(ExtCollections.asSet(
                SampleServicePort.EurekaServerPort.build()
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
                InstanceModel model = InstanceModel.getDefaultModel();

                int cidx = idx.incrementAndGet();
                String name = baseName + '_' + cidx;
                NetworkAddress publicAddress = publicAddresses.next();
                NetworkAddress privateAddress = privateAddresses.next();
                DataCenterInfo dataCenter = InstanceModel.getDefaultModel().newAwsDataCenterInfo()
                        .withAwsDataCenter(templateDataCenter)
                        .withInstanceId(String.format("i-%s-%08d", baseName, cidx))
                        .withPublicHostName(publicAddress.getHostName())
                        .withPublicIPv4(publicAddress.getIpAddress())
                        .withPrivateHostName(privateAddress.getHostName())
                        .withPrivateIPv4(privateAddress.getIpAddress())
                        .build();
                return model.newInstanceInfo()
                        .withId("id#" + name)
                        .withApp(template.getApp())
                        .withAppGroup(template.getAppGroup())
                        .withAsg(template.getAsg())
                        .withHealthCheckUrls(template.getHealthCheckUrls())
                        .withHomePageUrl(template.getHomePageUrl())
                        .withPorts((HashSet<ServicePort>) template.getPorts())
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
