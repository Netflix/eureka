package com.netflix.eureka2.server.service;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.ServicePort;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.transport.tcp.discovery.TcpDiscoveryServer;
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
public class EurekaReadServerHealthService extends EurekaServerHealthService {

    private final TcpDiscoveryServer discoveryServer;
    private final EurekaClient eurekaClient;

    @Inject
    public EurekaReadServerHealthService(
            EurekaServerConfig config,
            TcpDiscoveryServer discoveryServer,
            EurekaClient eurekaClient
    ) {
        super(config);
        this.discoveryServer = discoveryServer;
        this.eurekaClient = eurekaClient;
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
        return eurekaClient.update(instanceInfo);
    }

    @Override
    protected Func1<InstanceInfo.Builder, InstanceInfo.Builder> resolveServersFunc() {
        return new Func1<InstanceInfo.Builder, InstanceInfo.Builder>() {
            @Override
            public InstanceInfo.Builder call(InstanceInfo.Builder builder) {
                HashSet<ServicePort> ports = new HashSet<>();
                ports.add(new ServicePort(Names.DISCOVERY, discoveryServer.serverPort(), false));

                return builder.withPorts(ports);
            }
        };
    }
}
