package com.netflix.eureka2.server.service;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
import com.netflix.eureka2.server.transport.tcp.registration.TcpRegistrationServer;
import com.netflix.eureka2.server.transport.tcp.replication.TcpReplicationServer;
import rx.Observable;
import rx.functions.Func1;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;

/**
 * @author David Liu
 */
@Singleton
public class EurekaWriteServerHealthService extends EurekaServerHealthService {

    private final TcpRegistrationServer registrationServer;
    private final TcpReplicationServer replicationServer;
    private final TcpDiscoveryServer discoveryServer;
    private final SourcedEurekaRegistry<InstanceInfo> registry;

    @Inject
    public EurekaWriteServerHealthService(
            EurekaServerConfig config,
            TcpRegistrationServer registrationServer,
            TcpReplicationServer replicationServer,
            TcpDiscoveryServer discoveryServer,
            SourcedEurekaRegistry registry
    ) {
        super(config);
        this.registrationServer = registrationServer;
        this.replicationServer = replicationServer;
        this.discoveryServer = discoveryServer;
        this.registry = registry;
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    @PreDestroy
    @Override
    public void shutdown() {
        super.shutdown();
    }

    @Override
    public Observable<Void> report(InstanceInfo instanceInfo) {
        return registry.register(instanceInfo).ignoreElements().cast(Void.class);
    }

    @Override
    protected Func1<InstanceInfo.Builder, InstanceInfo.Builder> resolveServersFunc() {
        return new Func1<InstanceInfo.Builder, InstanceInfo.Builder>() {
            @Override
            public InstanceInfo.Builder call(InstanceInfo.Builder builder) {
                HashSet<ServicePort> ports = new HashSet<>();
                ports.add(new ServicePort(Names.REGISTRATION, registrationServer.serverPort(), false));
                ports.add(new ServicePort(Names.REPLICATION, replicationServer.serverPort(), false));
                ports.add(new ServicePort(Names.DISCOVERY, discoveryServer.serverPort(), false));

                return builder.withPorts(ports);
            }
        };
    }
}
