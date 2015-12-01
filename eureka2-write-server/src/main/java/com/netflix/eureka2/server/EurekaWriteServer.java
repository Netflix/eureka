package com.netflix.eureka2.server;

import javax.inject.Inject;

import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.server.transport.RegistrationTransportServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaWriteServer extends AbstractEurekaServer {

    @Inject
    public EurekaWriteServer(Injector injector) {
        super(injector);
    }

    public int getRegistrationPort() {
        return injector.getInstance(RegistrationTransportServer.class).getServerPort();
    }

    @Override
    public int getInterestPort() {
        return getRegistrationPort();
    }

    public int getReplicationPort() {
        return injector.getInstance(TcpReplicationServer.class).serverPort();
    }

    public EurekaRegistry<InstanceInfo> getEurekaServerRegistry() {
        return injector.getInstance(EurekaRegistry.class);
    }
}
