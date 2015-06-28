package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Injector;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.EurekaWriteServer;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EmbeddedWriteServer extends EurekaWriteServer {

    @Inject
    public EmbeddedWriteServer(Injector injector) {
        super(injector);
    }

    public ServerResolver getRegistrationResolver() {
        return ServerResolvers.fromHostname("localhost")
                .withPort(injector.getInstance(EurekaWriteServer.class).getRegistrationPort());
    }

    public ServerResolver getInterestResolver() {
        return ServerResolvers.fromHostname("localhost")
                .withPort(injector.getInstance(EurekaWriteServer.class).getInterestPort());
    }

    public WriteServerReport serverReport() {
        EurekaWriteServer eurekaWriteServer = injector.getInstance(EurekaWriteServer.class);
        return new WriteServerReport(
                eurekaWriteServer.getRegistrationPort(),
                eurekaWriteServer.getInterestPort(),
                eurekaWriteServer.getReplicationPort(),
                getEurekaServerRegistry().size(),
                getHttpServerPort(),
                getWebAdminPort()
        );
    }

    public static class WriteServerReport extends AbstractServerReport {
        private final int registrationPort;
        private final int discoveryPort;
        private final int replicationPort;
        private final int registrySize;

        public WriteServerReport(int registrationPort, int discoveryPort, int replicationPort,
                                 int registrySize, int httpServerPort, int adminPort) {
            super(httpServerPort, adminPort);
            this.registrationPort = registrationPort;
            this.discoveryPort = discoveryPort;
            this.replicationPort = replicationPort;
            this.registrySize = registrySize;
        }

        public int getRegistrationPort() {
            return registrationPort;
        }

        public int getDiscoveryPort() {
            return discoveryPort;
        }

        public int getReplicationPort() {
            return replicationPort;
        }

        public int getRegistrySize() {
            return registrySize;
        }
    }
}
