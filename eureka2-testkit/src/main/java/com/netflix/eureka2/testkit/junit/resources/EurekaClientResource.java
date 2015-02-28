package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClientBuilder;
import com.netflix.eureka2.client.resolver.ServerResolver;
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

    private EurekaRegistrationClient registrationClient;
    private EurekaInterestClient interestClient;

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

    public EurekaRegistrationClient getRegistrationClient() {
        return registrationClient;
    }

    public EurekaInterestClient getInterestClient() {
        return interestClient;
    }

    public InstanceInfo getClientInfo() {
        return clientInfo;
    }

    @Override
    protected void before() throws Throwable {
        registrationClient = new EurekaRegistrationClientBuilder()
                .withTransportConfig(transportConfig)
                .fromWriteServerResolver(writeServerResource.getRegistrationResolver())
                .build();

        ServerResolver readResolverToUse = readServerResource == null
                ? writeServerResource.getInterestResolver()
                : readServerResource.getInterestResolver();

        interestClient = new EurekaInterestClientBuilder()
                .withTransportConfig(transportConfig)
                .fromReadServerResolver(readResolverToUse)
                .build();
    }

    @Override
    protected void after() {
        if (registrationClient != null) {
            registrationClient.shutdown();
        }
        if (interestClient != null) {
            interestClient.shutdown();
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
