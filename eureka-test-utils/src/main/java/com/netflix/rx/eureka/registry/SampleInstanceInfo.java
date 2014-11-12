package com.netflix.rx.eureka.registry;

import com.netflix.rx.eureka.registry.InstanceInfo.Builder;
import com.netflix.rx.eureka.registry.InstanceInfo.Status;
import com.netflix.rx.eureka.utils.Sets;

import java.util.HashSet;
import java.util.Random;
import java.util.UUID;

/**
 * @author Tomasz Bak
 */
public enum SampleInstanceInfo {

    ZuulServer() {
        @Override
        public Builder builder() {
            return builder(this.name());
        }
    },

    DiscoveryServer() {
        @Override
        public Builder builder() {
            return builder(this.name());
        }
    },

    CliServer() {
        @Override
        public Builder builder() {
            return builder(this.name());
        }
    };

    public abstract Builder builder();

    protected Builder builder(String name) {
        HashSet<String> healthCheckUrls = new HashSet<>();
        healthCheckUrls.add("http://eureka/healthCheck/"+name+"1");
        healthCheckUrls.add("http://eureka/healthCheck/"+name+"2");
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
                .withPorts(Sets.asSet(new ServicePort(7200, false), new ServicePort(7210, true)))
                .withSecureVipAddress("vipSecure#"+name)
                .withStatus(Status.UP)
                .withStatusPageUrl("http://eureka/status/"+name)
                .withVipAddress("vip#"+name)
                .withMetaData("optionA", "valueA")
                .withMetaData("optionB", "valueB")
                .withDataCenterInfo(SampleAwsDataCenterInfo.UsEast1a.build());
    }

    public InstanceInfo build() {
        return builder().build();
    }

    protected static String randomIp() {
        Random r = new Random();
        return r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256) + "." + r.nextInt(256);
    }
}
