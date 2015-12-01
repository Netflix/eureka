package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServerBuilder;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;
import rx.Observable;

import static com.netflix.eureka2.server.config.bean.BootstrapConfigBean.aBootstrapConfig;
import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static com.netflix.eureka2.server.config.bean.WriteServerConfigBean.aWriteServerConfig;

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
        WriteServerConfig config = aWriteServerConfig()
                .withInstanceInfoConfig(
                        anEurekaInstanceInfoConfig()
                                .withEurekaApplicationName(name)
                                .withEurekaVipAddress(name)
                                .build()
                )
                .withTransportConfig(
                        anEurekaServerTransportConfig()
                                .withCodec(codec)
                                .withHttpPort(0)
                                .withInterestPort(0)
                                .withRegistrationPort(0)
                                .withReplicationPort(0)
                                .withShutDownPort(0)
                                .withWebAdminPort(0)
                                .build()
                )
                .withBootstrapConfig(aBootstrapConfig().withBootstrapEnabled(false).build())
                .build();

        Observable<ChangeNotification<Server>> noPeers = Observable.never();
        server = new EmbeddedWriteServerBuilder()
                .withConfiguration(config)
                .withReplicationPeers(noPeers)
                .build();
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
        return server.getInterestPort();
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
