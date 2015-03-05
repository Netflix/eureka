package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class WriteServerResource extends EurekaExternalResource {

    public static final String DEFAULT_WRITE_CLUSTER_NAME = "write-test";

    private final String name;
    private final Codec codec;

    private EmbeddedWriteServer server;

    public WriteServerResource() {
        this(DEFAULT_WRITE_CLUSTER_NAME);
    }

    public WriteServerResource(String name) {
        this(name, Codec.Avro);
    }

    public WriteServerResource(String name, Codec codec) {
        this.name = name;
        this.codec = codec;
    }

    @Override
    protected void before() throws Throwable {
        WriteServerConfig config = WriteServerConfig.writeBuilder()
                .withAppName(name)
                .withVipAddress(name)
                .withDataCenterType(DataCenterType.Basic)
                .withHttpPort(0)
                .withRegistrationPort(0)
                .withDiscoveryPort(0)
                .withReplicationPort(0)
                .withCodec(codec)
                .withShutDownPort(0)
                .withWebAdminPort(0)
                .withReplicationRetryMillis(1000)
                .build();

        server = new EmbeddedWriteServer(config, Observable.<ChangeNotification<Server>>never(), false, false);
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
        return server.getInterestServerResolver();
    }

    public EmbeddedWriteServer getServer() {
        return server;
    }
}
