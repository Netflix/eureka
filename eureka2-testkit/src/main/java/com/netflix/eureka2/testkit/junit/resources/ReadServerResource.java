package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class ReadServerResource extends ExternalResource {

    public static final String DEFAULT_READ_CLUSTER_NAME = "read-test";

    private final String name;
    private final WriteServerResource writeServerResource;
    private final Codec codec;

    private EmbeddedReadServer server;
    private int discoveryPort;

    public ReadServerResource(WriteServerResource writeServerResource) {
        this(DEFAULT_READ_CLUSTER_NAME, writeServerResource);
    }

    public ReadServerResource(String name, WriteServerResource writeServerResource) {
        this(name, writeServerResource, Codec.Avro);
    }

    public ReadServerResource(String name, WriteServerResource writeServerResource, Codec codec) {
        this.name = name;
        this.writeServerResource = writeServerResource;
        this.codec = codec;
    }

    @Override
    protected void before() throws Throwable {
        EurekaServerConfig config = EurekaServerConfig.baseBuilder()
                .withAppName(name)
                .withVipAddress(name)
                .withDataCenterType(DataCenterType.Basic)
                .withHttpPort(0)
                .withDiscoveryPort(0)
                .withShutDownPort(0)
                .withWebAdminPort(0)
                .withCodec(codec)
                .build();
        ServerResolver registrationResolver = ServerResolver.withHostname("localhost").withPort(writeServerResource.getRegistrationPort());
        ServerResolver discoveryResolver = ServerResolver.withHostname("localhost").withPort(writeServerResource.getDiscoveryPort());
        server = new EmbeddedReadServer(config, registrationResolver, discoveryResolver, false, false);
        server.start();

        // Find ephemeral port numbers
        discoveryPort = server.getInjector().getInstance(TcpDiscoveryServer.class).serverPort();
    }

    @Override
    protected void after() {
        if (server != null) {
            server.shutdown();
        }
    }

    public String getName() {
        return name;
    }

    public int getDiscoveryPort() {
        return discoveryPort;
    }

    public ServerResolver getInterestResolver() {
        return ServerResolver.withHostname("localhost").withPort(discoveryPort);
    }
}
