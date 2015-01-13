package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.data.builder.SampleServicePort;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class EurekaClientResource extends ExternalResource {

    private final WriteServerResource writeServerResource;
    private final ReadServerResource readServerResource;
    private final Codec codec;
    private final InstanceInfo clientInfo;

    private EurekaClient eurekaClient;

    public EurekaClientResource(String name, WriteServerResource writeServerResource) {
        this(name, writeServerResource, null);
    }

    public EurekaClientResource(String name, WriteServerResource writeServerResource,
                                ReadServerResource readServerResource) {
        this(name, writeServerResource, readServerResource, Codec.Avro);
    }

    public EurekaClientResource(String name, WriteServerResource writeServerResource,
                                ReadServerResource readServerResource, Codec codec) {
        this.writeServerResource = writeServerResource;
        this.readServerResource = readServerResource;
        this.codec = codec;
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
            ).withCodec(codec).build();
        } else {
            eurekaClient = new EurekaClientBuilder(
                    readServerResource.getDiscoveryResolver(),
                    writeServerResource.getRegistrationResolver()
            ).withCodec(codec).build();
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
