package com.netflix.eureka2.testkit.embedded.cluster;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolver.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.testkit.embedded.cluster.EmbeddedReadCluster.ReadClusterReport;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedReadServer.ReadServerReport;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.MembershipEvent;
import netflix.ocelli.MembershipEvent.EventType;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadCluster extends EmbeddedEurekaCluster<EmbeddedReadServer, Server, ReadClusterReport> {

    private static final String READ_SERVER_NAME = "eureka2-read";
    private static final int READ_SERVER_PORTS_FROM = 14000;

    private final ServerResolver registrationResolver;
    private final ServerResolver discoveryResolver;
    private final boolean withExt;
    private final boolean withAdminUI;
    private final boolean ephemeralPorts;

    private int nextAvailablePort = READ_SERVER_PORTS_FROM;

    public EmbeddedReadCluster(ServerResolver registrationResolver,
                               ServerResolver discoveryResolver,
                               boolean withExt,
                               boolean withAdminUI,
                               boolean ephemeralPorts) {
        super(READ_SERVER_NAME);
        this.registrationResolver = registrationResolver;
        this.discoveryResolver = discoveryResolver;
        this.withExt = withExt;
        this.withAdminUI = withAdminUI;
        this.ephemeralPorts = ephemeralPorts;
    }

    @Override
    public int scaleUpByOne() {
        int discoveryPort = ephemeralPorts ? 0 : nextAvailablePort;

        EurekaServerConfig config = EurekaServerConfig.baseBuilder()
                .withAppName(READ_SERVER_NAME)
                .withVipAddress(READ_SERVER_NAME)
                .withDataCenterType(DataCenterType.Basic)
                .withDiscoveryPort(discoveryPort)
                .withShutDownPort(nextAvailablePort + 1)
                .withWebAdminPort(nextAvailablePort + 2)
                .withCodec(Codec.Avro)
                .build();

        EmbeddedReadServer newServer = newServer(config);
        newServer.start();

        nextAvailablePort += 10;

        if(ephemeralPorts) {
            discoveryPort = newServer.getDiscoveryPort();
        }

        return scaleUpByOne(newServer, new Server("localhost", discoveryPort));
    }

    protected EmbeddedReadServer newServer(EurekaServerConfig config) {
        return new EmbeddedReadServer(config, registrationResolver, discoveryResolver, withExt, withAdminUI);
    }

    @Override
    public ReadClusterReport clusterReport() {
        List<ReadServerReport> serverReports = new ArrayList<>();
        for (EmbeddedReadServer server : servers) {
            serverReports.add(server.serverReport());
        }
        return new ReadClusterReport(serverReports);
    }

    public ServerResolver discoveryResolver() {
        Observable<MembershipEvent<Server>> events = clusterChangeObservable()
                .map(new Func1<ChangeNotification<Server>, MembershipEvent<Server>>() {
                    @Override
                    public MembershipEvent<Server> call(ChangeNotification<Server> notification) {
                        Server server = notification.getData();
                        switch (notification.getKind()) {
                            case Add:
                                return new MembershipEvent<>(EventType.ADD, server);
                            case Modify:
                                throw new IllegalStateException("Modify not expected");
                            case Delete:
                                return new MembershipEvent<Server>(EventType.REMOVE, server);
                        }
                        return null;
                    }
                });
        final LoadBalancer<Server> loadBalancer = new DefaultLoadBalancerBuilder<Server>(events).build();
        return new ServerResolver() {
            @Override
            public Observable<Server> resolve() {
                return loadBalancer.choose();
            }

            @Override
            public void close() {
                loadBalancer.shutdown();
            }
        };
    }

    public static class ReadClusterReport {

        private final List<ReadServerReport> serverReports;

        public ReadClusterReport(List<ReadServerReport> serverReports) {
            this.serverReports = serverReports;
        }

        public List<ReadServerReport> getServerReports() {
            return serverReports;
        }
    }
}
