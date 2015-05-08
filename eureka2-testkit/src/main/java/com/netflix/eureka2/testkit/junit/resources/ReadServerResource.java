package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;
import com.netflix.eureka2.codec.CodecType;

/**
 * @author Tomasz Bak
 */
public class ReadServerResource extends EurekaExternalResource {

    public static final String DEFAULT_READ_CLUSTER_NAME = "read-test";
    public static final String EMBEDDED_READ_CLIENT_ID = "embeddedReadClient";

    private final String name;
    private final WriteServerResource writeServerResource;
    private final CodecType codec;

    private EmbeddedReadServer server;
    private int discoveryPort;

    public ReadServerResource(WriteServerResource writeServerResource) {
        this(DEFAULT_READ_CLUSTER_NAME, writeServerResource);
    }

    public ReadServerResource(String name, WriteServerResource writeServerResource) {
        this(name, writeServerResource, CodecType.Avro);
    }

    public ReadServerResource(String name, WriteServerResource writeServerResource, CodecType codec) {
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
        ServerResolver registrationResolver = ServerResolvers.fromHostname("localhost").withPort(writeServerResource.getRegistrationPort());
        ServerResolver discoveryResolver = ServerResolvers.fromHostname("localhost").withPort(writeServerResource.getDiscoveryPort());
        server = new EmbeddedReadServer(EMBEDDED_READ_CLIENT_ID, config, registrationResolver, discoveryResolver, false, false);
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
        return ServerResolvers.fromHostname("localhost").withPort(discoveryPort);
    }
}
