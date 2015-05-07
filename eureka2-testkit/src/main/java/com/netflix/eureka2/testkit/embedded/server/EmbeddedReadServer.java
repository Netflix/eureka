package com.netflix.eureka2.testkit.embedded.server;

import java.util.Properties;

import com.google.inject.Module;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.channel.ClientChannelFactory;
import com.netflix.eureka2.client.channel.InterestChannelFactory;
import com.netflix.eureka2.client.interest.BatchAwareIndexRegistry;
import com.netflix.eureka2.client.interest.BatchingRegistry;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.BasicEurekaRegistryConfig;
import com.netflix.eureka2.config.BasicEurekaRegistryConfig.Builder;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.eureka1.rest.Eureka1Configuration;
import com.netflix.eureka2.eureka1.rest.Eureka1RestApiModule;
import com.netflix.eureka2.interests.IndexRegistryImpl;
import com.netflix.eureka2.registry.PreservableEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.EurekaReadServerModule;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.interest.FullFetchBatchingRegistry;
import com.netflix.eureka2.server.interest.FullFetchInterestClient;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer.ReadServerReport;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.metric.client.EurekaClientMetricFactory.clientMetrics;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServer extends EmbeddedEurekaServer<EurekaServerConfig, ReadServerReport> {
    private final String serverId;
    private final ServerResolver registrationResolver;
    private final ServerResolver discoveryResolver;

    public EmbeddedReadServer(String serverId,
                              EurekaServerConfig config,
                              ServerResolver registrationResolver,
                              ServerResolver discoveryResolver,
                              boolean withExt,
                              boolean withDashboard) {
        super(ServerType.Read, config, withExt, withDashboard);
        this.serverId = serverId;
        this.registrationResolver = registrationResolver;
        this.discoveryResolver = discoveryResolver;
    }

    @Override
    public void start() {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withServerResolver(registrationResolver)
                .build();

        // TODO We need to better encapsulate EurekaInterestClient construction
        BatchingRegistry<InstanceInfo> remoteBatchingRegistry = new FullFetchBatchingRegistry<>();
        BatchAwareIndexRegistry<InstanceInfo> indexRegistry = new BatchAwareIndexRegistry<>(
                new IndexRegistryImpl<InstanceInfo>(), remoteBatchingRegistry);

        BasicEurekaRegistryConfig registryConfig = new Builder().build();
        BasicEurekaTransportConfig transportConfig = new BasicEurekaTransportConfig.Builder().build();

        PreservableEurekaRegistry registry = new PreservableEurekaRegistry(
                new SourcedEurekaRegistryImpl(indexRegistry, registryMetrics()),
                registryConfig,
                registryMetrics()
        );

        ClientChannelFactory<InterestChannel> channelFactory = new InterestChannelFactory(
                serverId,
                transportConfig,
                discoveryResolver,
                registry,
                remoteBatchingRegistry,
                clientMetrics()
        );

        EurekaInterestClient interestClient = new FullFetchInterestClient(registry, channelFactory);

        Module[] modules = {
                new EurekaReadServerModule(config, registrationClient, interestClient),
                new Eureka1RestApiModule(new Eureka1Configuration(), registrationClient)
        };

        setup(modules);
    }

    @Override
    protected void loadInstanceProperties(Properties props) {
        super.loadInstanceProperties(props);
        props.setProperty("eureka.client.discovery-endpoint.port", Integer.toString(config.getDiscoveryPort()));
    }

    public int getDiscoveryPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(TcpDiscoveryServer.class).serverPort();
    }

    @Override
    public ServerResolver getInterestResolver() {
        return ServerResolvers.fromHostname("localhost").withPort(getDiscoveryPort());
    }

    @Override
    public ReadServerReport serverReport() {
        return new ReadServerReport(getDiscoveryPort(), getHttpServerPort(), getWebAdminPort());
    }

    public static class ReadServerReport extends AbstractServerReport {
        private final int discoveryPort;

        public ReadServerReport(int discoveryPort, int httpServerPort, int adminPort) {
            super(httpServerPort, adminPort);
            this.discoveryPort = discoveryPort;
        }

        public int getDiscoveryPort() {
            return discoveryPort;
        }
    }
}
