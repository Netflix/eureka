package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;
import com.netflix.eureka2.codec.CodecType;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class WriteServerResource extends EurekaExternalResource {

    public static final String DEFAULT_WRITE_CLUSTER_NAME = "write-test";

    private final String name;
    private final String readClusterName;
    private final CodecType codec;

    private EmbeddedWriteServer server;

    public WriteServerResource() {
        this(DEFAULT_WRITE_CLUSTER_NAME, ReadServerResource.DEFAULT_READ_CLUSTER_NAME);
    }

    public WriteServerResource(String name, String readClusterName) {
        this(name, readClusterName, CodecType.Avro);
    }

    public WriteServerResource(String name, String readClusterName, CodecType codec) {
        this.name = name;
        this.readClusterName = readClusterName;
        this.codec = codec;
    }

    @Override
    protected void before() throws Throwable {
        WriteServerConfig config = WriteServerConfig.writeBuilder()
                .withAppName(name)
                .withVipAddress(name)
                .withReadClusterVipAddress(readClusterName)
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

        Observable<ChangeNotification<Server>> noPeers = Observable.never();
        server = new EmbeddedWriteServer(config, noPeers, noPeers, false, false);
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

    public ServerResolver getInterestResolver() {
        return server.getInterestResolver();
    }

    public EmbeddedWriteServer getServer() {
        return server;
    }
}
