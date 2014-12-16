package com.netflix.eureka2.testkit.embedded.server;

import com.google.inject.Module;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.server.EurekaReadServerModule;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer.ReadServerReport;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServer extends EmbeddedEurekaServer<EurekaServerConfig, ReadServerReport> {
    private final ServerResolver registrationResolver;
    private final ServerResolver discoveryResolver;

    public EmbeddedReadServer(EurekaServerConfig config,
                              final ServerResolver registrationResolver,
                              ServerResolver discoveryResolver,
                              boolean withExt,
                              boolean withDashboard) {
        super(config, withExt, withDashboard);
        this.registrationResolver = registrationResolver;
        this.discoveryResolver = discoveryResolver;
    }

    @Override
    public void start() {
        final EurekaClient eurekaClient = Eureka.newClientBuilder(discoveryResolver, registrationResolver)
                .withCodec(config.getCodec()).build();
        Module[] modules = {
                new EurekaReadServerModule(config, eurekaClient)
        };

        setup(modules);
    }

    @Override
    public ReadServerReport serverReport() {
        return new ReadServerReport(
                config.getDiscoveryPort(),
                formatAdminURI()
        );
    }

    public static class ReadServerReport {
        private final int discoveryPort;
        private final String adminURI;

        public ReadServerReport(int discoveryPort, String adminURI) {
            this.discoveryPort = discoveryPort;
            this.adminURI = adminURI;
        }

        public int getDiscoveryPort() {
            return discoveryPort;
        }

        public String getAdminURI() {
            return adminURI;
        }
    }
}
