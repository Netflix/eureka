package com.netflix.eureka2.testkit.embedded.server;

import java.net.InetSocketAddress;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.server.EurekaWriteServerModule;
import com.netflix.eureka2.server.ReplicationPeerAddressesProvider;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer.WriteServerReport;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteServer extends EmbeddedEurekaServer<WriteServerConfig, WriteServerReport> {

    public EmbeddedWriteServer(final WriteServerConfig config,
                               final Observable<ChangeNotification<InetSocketAddress>> replicationPeers,
                               boolean withExt,
                               boolean withDashboards) {
        super(config, withExt, withDashboards);
        Module[] modules = {
                new EurekaWriteServerModule(config),
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(ReplicationPeerAddressesProvider.class).toInstance(new ReplicationPeerAddressesProvider(replicationPeers));
                    }
                }
        };

        setup(modules);
    }

    @Override
    public WriteServerReport serverReport() {
        return new WriteServerReport(
                config.getRegistrationPort(),
                config.getDiscoveryPort(),
                config.getReplicationPort(),
                formatAdminURI(),
                getEurekaServerRegistry().size()
        );
    }

    public static class WriteServerReport {
        private final int registrationPort;
        private final int discoveryPort;
        private final int replicationPort;
        private final String adminURI;
        private final int registrySize;

        public WriteServerReport(int registrationPort, int discoveryPort, int replicationPort, String adminURI, int registrySize) {
            this.registrationPort = registrationPort;
            this.discoveryPort = discoveryPort;
            this.replicationPort = replicationPort;
            this.adminURI = adminURI;
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

        public String getAdminURI() {
            return adminURI;
        }

        public int getRegistrySize() {
            return registrySize;
        }
    }
}
