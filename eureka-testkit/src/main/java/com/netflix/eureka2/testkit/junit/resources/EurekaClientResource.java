package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.InstanceInfo.Builder;
import com.netflix.eureka2.registry.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class EurekaClientResource extends ExternalResource {

    private final WriteServerResource writeServerResource;
    private final ReadServerResource readServerResource;
    private final InstanceInfo clientInfo;

    private EurekaClient eurekaClient;

    public EurekaClientResource(String name, WriteServerResource writeServerResource) {
        this(name, writeServerResource, null);
    }

    public EurekaClientResource(String name, WriteServerResource writeServerResource,
                                ReadServerResource readServerResource) {
        this.writeServerResource = writeServerResource;
        this.readServerResource = readServerResource;
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
            eurekaClient = new EurekaClientBuilder(
                    writeServerResource.getDiscoveryResolver(),
                    writeServerResource.getRegistrationResolver()
            ).build();
        } else {
            eurekaClient = new EurekaClientBuilder(
                    readServerResource.getDiscoveryResolver(),
                    writeServerResource.getRegistrationResolver()
            ).build();
        }
    }

    @Override
    protected void after() {
        if (eurekaClient != null) {
            eurekaClient.close();
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
