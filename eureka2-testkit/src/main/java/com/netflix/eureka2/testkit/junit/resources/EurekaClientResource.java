package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import org.junit.rules.ExternalResource;

/**
 * TODO is this still needed or superseded by EurekaDeploymentResource.connectTo ... ?
 * @author Tomasz Bak
 */
public class EurekaClientResource extends ExternalResource {

    private final EurekaTransportConfig transportConfig;
    private final WriteServerResource writeServerResource;
    private final ReadServerResource readServerResource;
    private final InstanceInfo clientInfo;

    private EurekaClient eurekaClient;

    public EurekaClientResource(String name, WriteServerResource writeServerResource) {
        this(name, writeServerResource, null);
    }

    public EurekaClientResource(String name, WriteServerResource writeServerResource,
                                ReadServerResource readServerResource) {
        this(name, writeServerResource, readServerResource, new BasicEurekaTransportConfig.Builder().build());
    }

    public EurekaClientResource(String name, WriteServerResource writeServerResource,
                                ReadServerResource readServerResource, EurekaTransportConfig transportConfig) {
        this.writeServerResource = writeServerResource;
        this.readServerResource = readServerResource;
        this.transportConfig = transportConfig;
        this.clientInfo = buildClientInfo(name);
    }

    public EurekaClient getEurekaClient() {
        return eurekaClient;
    }

    public InstanceInfo getClientInfo() {
        return clientInfo;
    }

    @Override
    protected void before() throws Throwable {
        if (readServerResource == null) {
            eurekaClient = EurekaClientBuilder.newBuilder()
                    .withReadServerResolver(writeServerResource.getDiscoveryResolver())
                    .withWriteServerResolver(writeServerResource.getRegistrationResolver())
                    .withTransportConfig(transportConfig)
                    .build();
        } else {
            eurekaClient = EurekaClientBuilder.newBuilder()
                    .withReadServerResolver(readServerResource.getDiscoveryResolver())
                    .withWriteServerResolver(writeServerResource.getRegistrationResolver())
                    .withTransportConfig(transportConfig)
                    .build();
        }
    }

    @Override
    protected void after() {
        if (eurekaClient != null) {
            eurekaClient.shutdown();
        }
    }

    protected InstanceInfo buildClientInfo(String name) {
        return new Builder()
                .withId("id#" + name)
                .withApp("app#" + name)
                .withAppGroup("appGroup#" + name)
                .withVipAddress("vip#" + name)
                .withStatus(Status.UP)
                .withPorts(SampleServicePort.httpPorts())
                .withDataCenterInfo(SampleAwsDataCenterInfo.UsEast1a.build())
                .build();
    }
}
