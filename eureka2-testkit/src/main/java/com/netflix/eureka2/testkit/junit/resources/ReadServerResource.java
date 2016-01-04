package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.EurekaTransportServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServerBuilder;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

import static com.netflix.eureka2.server.config.bean.EurekaInstanceInfoConfigBean.anEurekaInstanceInfoConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerConfigBean.anEurekaServerConfig;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;

/**
 * @author Tomasz Bak
 */
public class ReadServerResource extends EurekaExternalResource {

    public static final String DEFAULT_READ_CLUSTER_NAME = "read-test";
    public static final String EMBEDDED_READ_CLIENT_ID = "embeddedReadClient";

    private final String name;
    private final WriteServerResource writeServerResource;

    private EmbeddedReadServer server;
    private int serverPort;

    public ReadServerResource(WriteServerResource writeServerResource) {
        this(DEFAULT_READ_CLUSTER_NAME, writeServerResource);
    }

    public ReadServerResource(String name, WriteServerResource writeServerResource) {
        this.name = name;
        this.writeServerResource = writeServerResource;
    }

    @Override
    protected void before() throws Throwable {
        EurekaServerConfig config = anEurekaServerConfig()
                .withInstanceInfoConfig(
                        anEurekaInstanceInfoConfig()
                                .withEurekaApplicationName(name)
                                .withEurekaVipAddress(name)
                                .build()
                )
                .withTransportConfig(
                        anEurekaServerTransportConfig()
                                .withHttpPort(0)
                                .withServerPort(0)
                                .withShutDownPort(0)
                                .withWebAdminPort(0)
                                .build()
                )
                .build();

        ServerResolver serverResolver = ServerResolvers.fromHostname("localhost").withPort(writeServerResource.getServerPort());
        server = new EmbeddedReadServerBuilder(EMBEDDED_READ_CLIENT_ID)
                .withConfiguration(config)
                .withRegistrationResolver(serverResolver)
                .withInterestResolver(serverResolver)
                .build();

        // Find ephemeral port numbers
        serverPort = server.getInjector().getInstance(EurekaTransportServer.class).getServerPort();
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

    public int getServerPort() {
        return serverPort;
    }

    public ServerResolver getInterestResolver() {
        return ServerResolvers.fromHostname("localhost").withPort(serverPort);
    }
}
