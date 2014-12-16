package com.netflix.eureka2.testkit.junit;

import java.net.InetSocketAddress;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import org.junit.rules.ExternalResource;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteServerResource extends ExternalResource {

    public static final String DEFAULT_WRITE_CLUSTER_NAME = "write-test";

    private final String name;

    private EmbeddedWriteServer server;
    private int registrationPort;
    private int discoveryPort;

    public EmbeddedWriteServerResource() {
        this(DEFAULT_WRITE_CLUSTER_NAME);
    }

    public EmbeddedWriteServerResource(String name) {
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

        // Find ephemeral port numbers
        registrationPort = server.getInjector().getInstance(TcpRegistrationServer.class).serverPort();
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

    public int getRegistrationPort() {
        return registrationPort;
    }

    public int getDiscoveryPort() {
        return discoveryPort;
    }
}
