package com.netflix.eureka;

import com.netflix.eureka.registry.Index;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfo.Builder;
import com.netflix.eureka.registry.InstanceInfo.Status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * @author Tomasz Bak
 */
public enum SampleInstanceInfo {

    ZuulServer() {
        @Override
        public Builder builder() {
            HashSet<String> healthCheckUrls = new HashSet<String>();
            healthCheckUrls.add("http://eureka/healthCheck/ZuulServer1");
            healthCheckUrls.add("http://eureka/healthCheck/ZuulServer2");
            HashSet<Integer> ports = new HashSet<Integer>();
            ports.add(80);
            ports.add(8080);
            HashSet<Integer> securePorts = new HashSet<Integer>();
            securePorts.add(443);
            securePorts.add(8443);
            return new Builder()
                    .withId("id#ZuulServer"+UUID.randomUUID().toString())
                    .withApp("app#ZuulServer")
                    .withAppGroup("group#ZuulServer")
                    .withAsg("asg#ZuulServer")
                    .withHealthCheckUrls(healthCheckUrls)
                    .withHomePageUrl("http://eureka/home/ZuulServer")
                    .withHostname("ZuulServer.test")
                    .withIp("192.168.0.1")
                    .withPorts(ports)
                    .withSecurePorts(securePorts)
                    .withSecureVipAddress("vipSecure#ZuulServer")
                    .withStatus(Status.UP)
                    .withStatusPageUrl("http://eureka/status/ZuulServer")
                    .withVipAddress("vip#ZuulServer");
        }

        @Override
        public String valueForIndex(Index index) {
            switch (index) {
                case AppGroup:
                    return "group#ZuulServer";
                case App:
                    return "app#ZuulServer";
                case Asg:
                    return "asg#ZuulServer";
                case VipAddress:
                    return "vip#ZuulServer";
                default:
                    return null;
            }
        }
    },
    DiscoveryServer() {
        @Override
        public Builder builder() {
            HashSet<String> healthCheckUrls = new HashSet<String>();
            healthCheckUrls.add("http://eureka/healthCheck/DiscoveryServer1");
            healthCheckUrls.add("http://eureka/healthCheck/DiscoveryServer2");
            HashSet<Integer> ports = new HashSet<Integer>();
            ports.add(80);
            ports.add(8080);
            HashSet<Integer> securePorts = new HashSet<Integer>();
            securePorts.add(443);
            securePorts.add(8443);
            return new Builder()
                    .withId("id#DiscoveryServer"+UUID.randomUUID().toString())
                    .withApp("app#DiscoveryServer")
                    .withAppGroup("group#DiscoveryServer")
                    .withAsg("asg#DiscoveryServer")
                    .withHealthCheckUrls(healthCheckUrls)
                    .withHomePageUrl("http://eureka/home/DiscoveryServer")
                    .withHostname("DiscoveryServer.test")
                    .withIp("192.168.0.1")
                    .withPorts(ports)
                    .withSecurePorts(securePorts)
                    .withSecureVipAddress("vipSecure#DiscoveryServer")
                    .withStatus(Status.UP)
                    .withStatusPageUrl("http://eureka/status/DiscoveryServer")
                    .withVipAddress("vip#DiscoveryServer");
        }

        @Override
        public String valueForIndex(Index index) {
            switch (index) {
                case AppGroup:
                    return "group#DiscoveryServer";
                case App:
                    return "app#DiscoveryServer";
                case Asg:
                    return "asg#DiscoveryServer";
                case VipAddress:
                    return "vip#DiscoveryServer";
                default:
                    return null;
            }
        }
    };

    public abstract Builder builder();

    abstract String valueForIndex(Index index);

    public InstanceInfo build() {
        return builder().build();
    }

    public static List<InstanceInfo> collectionOf(int n) {
        return collectionOf(n, ZuulServer.builder(), DiscoveryServer.builder());
    }

    public static List<InstanceInfo> collectionOf(int n, Builder... builders) {
        Random random = new Random();
        List<InstanceInfo> list = new ArrayList<InstanceInfo>(n);
        for (int i = 0; i < n; i++) {
            list.add(randomize(i, builders[random.nextInt(builders.length)]));
        }
        return list;
    }

    private static InstanceInfo randomize(int id, Builder builder) {
        return builder.withId(Integer.toString(id)).build();
    }
}
