package com.netflix.eureka2.testkit.junit.resources;

import java.net.InetSocketAddress;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import org.junit.rules.ExternalResource;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class WriteServerResource extends ExternalResource {

    public static final String DEFAULT_WRITE_CLUSTER_NAME = "write-test";

    private final String name;

    private EmbeddedWriteServer server;

    public WriteServerResource() {
        this(DEFAULT_WRITE_CLUSTER_NAME);
    }

    public WriteServerResource(String name) {
        this.name = name;
    }

    @Override
    protected void before() throws Throwable {
        WriteServerConfig config = WriteServerConfig.writeBuilder()
                .withAppName(name)
                .withVipAddress(name)
                .withDataCenterType(DataCenterType.Basic)
                .withRegistrationPort(0)
                .withDiscoveryPort(0)
                .withReplicationPort(0)
                .withCodec(Codec.Avro)
                .withShutDownPort(0)
                .withWebAdminPort(0)
                .withReplicationRetryMillis(1000)
                .build();

        server = new EmbeddedWriteServer(config, Observable.<ChangeNotification<InetSocketAddress>>never(), false, false);
        server.start();
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

    public int getRegistrationPort() {
        return server.getRegistrationPort();
    }

    public int getDiscoveryPort() {
        return server.getDiscoveryPort();
    }

    public ServerResolver getRegistrationResolver() {
        return server.getRegistrationResolver();
    }

    public ServerResolver getDiscoveryResolver() {
        return server.getDiscoveryResolver();
    }
}
