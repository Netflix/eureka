package com.netflix.eureka2.testkit.junit;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServerResource extends ExternalResource {

    public static final String DEFAULT_READ_CLUSTER_NAME = "read-test";

    private final String name;
    private final EmbeddedWriteServerResource writeServerResource;

    private EmbeddedReadServer server;
    private int discoveryPort;

    public EmbeddedReadServerResource(EmbeddedWriteServerResource writeServerResource) {
        this(DEFAULT_READ_CLUSTER_NAME, writeServerResource);
    }

    public EmbeddedReadServerResource(String name, EmbeddedWriteServerResource writeServerResource) {
        this.name = name;
        this.writeServerResource = writeServerResource;
    }

    @Override
    protected void before() throws Throwable {
        EurekaServerConfig config = EurekaServerConfig.baseBuilder()
                .withAppName(name)
                .withVipAddress(name)
                .withDataCenterType(DataCenterType.Basic)
                .withDiscoveryPort(0)
                .withShutDownPort(0)
                .withWebAdminPort(0)
                .withCodec(Codec.Avro)
                .build();
        ServerResolver registrationResolver = ServerResolvers.just("localhost", writeServerResource.getRegistrationPort());
        ServerResolver discoveryResolver = ServerResolvers.just("localhost", writeServerResource.getDiscoveryPort());
        server = new EmbeddedReadServer(config, registrationResolver, discoveryResolver, false, false);

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
}
