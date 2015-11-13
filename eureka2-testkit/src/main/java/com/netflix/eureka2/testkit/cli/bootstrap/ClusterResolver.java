package com.netflix.eureka2.testkit.cli.bootstrap;

import javax.naming.NamingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.interest.Interest.Operator;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.interests.host.DnsResolver;
import com.netflix.eureka2.internal.util.SystemUtil;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.NetworkAddress;
import com.netflix.eureka2.model.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.model.instance.ServiceEndpoint;
import com.netflix.eureka2.model.instance.ServicePort;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.selector.ServiceSelector;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.testkit.cli.ClusterTopology;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.functions.Func2;

import static com.netflix.eureka2.server.resolver.EurekaClusterResolvers.readClusterResolverFromConfiguration;
import static com.netflix.eureka2.server.resolver.EurekaClusterResolvers.writeClusterResolverFromConfiguration;

/**
 * This class resolves write and read server nodes, with minimum amount of information needed.
 * As a minimum a server address is required. If no read/write vip names are provided, a pattern is applied
 * and result set is filtered using bootstrap IP address. If interest port is not provided, a default value is
 * assumed.
 *
 * @author Tomasz Bak
 */
public class ClusterResolver {

    private static final ServiceSelector REGISTRATION_SERVICE_SELECTOR = ServiceSelector
            .selectBy()
            .serviceLabel(Names.REGISTRATION).publicIp(true).protocolType(ProtocolType.IPv4)
            .or()
            .serviceLabel(Names.REGISTRATION);

    private static final ServiceSelector REPLICATION_SERVICE_SELECTOR = ServiceSelector
            .selectBy()
            .serviceLabel(Names.REPLICATION).publicIp(true).protocolType(ProtocolType.IPv4)
            .or()
            .serviceLabel(Names.REPLICATION);

    private static final ServiceSelector INTEREST_SERVICE_SELECTOR = ServiceSelector
            .selectBy()
            .serviceLabel(Names.INTEREST).publicIp(true).protocolType(ProtocolType.IPv4)
            .or()
            .serviceLabel(Names.INTEREST);

    private static final String EUREKA_VIP_PATTERN = "(?i)eureka.*(write|read).*";
    // TODO We should not need this, but as Eureka read registration is broken, we cannot depend on the service label.
    private static final String EUREKA_READ_VIP_PATTERN = "(?i)eureka.*read.*";

    private final String bootstrapAddress;
    private final List<String> resolvedBootstrapServers;
    private int interestPort;
    private ClusterAddress resolvedClusterAddress;
    private String bootstrapVip;
    private String readClusterVip;

    private final EurekaTransportConfig transportConfig;
    private final Scheduler scheduler;
    private ServerType serverType;

    public ClusterResolver(String bootstrapAddress,
                           int interestPort,
                           String bootstrapVip,
                           String readClusterVip,
                           EurekaTransportConfig transportConfig,
                           Scheduler scheduler) {
        this.bootstrapAddress = bootstrapAddress;
        this.resolvedBootstrapServers = new ArrayList<>(resolveBootstrapServers(bootstrapAddress));
        this.interestPort = interestPort;
        this.bootstrapVip = bootstrapVip == null ? EUREKA_VIP_PATTERN : bootstrapVip;
        this.readClusterVip = readClusterVip;
        this.transportConfig = transportConfig;
        this.scheduler = scheduler;
    }

    public String getBootstrapAddress() {
        return bootstrapAddress;
    }

    public String getReadClusterVip() {
        return readClusterVip;
    }

    public ServerType getServerType() {
        return serverType;
    }

    public EurekaTransportConfig getTransportConfig() {
        return transportConfig;
    }

    public Observable<ClusterTopology> connect() {
        final boolean isDnsName = isDnsName();
        return Observable.create(new OnSubscribe<ClusterTopology>() {
            @Override
            public void call(Subscriber<? super ClusterTopology> subscriber) {
                resolveServerType()
                        .flatMap(new Func1<ServerType, Observable<ClusterTopology>>() {
                            @Override
                            public Observable<ClusterTopology> call(ServerType serverType) {
                                Observable<List<ClusterAddress>> writeServers;
                                Observable<List<ClusterAddress>> readServers;
                                if (serverType == ServerType.Write) {
                                    writeServers = combine(writeClusterResolverFromConfiguration(resolvedClusterAddress, isDnsName, scheduler).clusterTopologyChanges());
                                    if (readClusterVip != null) {
                                        readServers = combine(resolveReadClusterFromEureka());
                                    } else {
                                        readServers = Observable.just(Collections.<ClusterAddress>emptyList());
                                    }
                                } else {
                                    writeServers = Observable.just(Collections.<ClusterAddress>emptyList());
                                    readServers = combine(readClusterResolverFromConfiguration(resolvedClusterAddress, isDnsName, scheduler).clusterTopologyChanges());
                                }

                                return Observable.combineLatest(
                                        writeServers.concatWith(Observable.<List<ClusterAddress>>never()),
                                        readServers.concatWith(Observable.<List<ClusterAddress>>never()),
                                        new Func2<List<ClusterAddress>, List<ClusterAddress>, ClusterTopology>() {
                                            @Override
                                            public ClusterTopology call(List<ClusterAddress> writeAddresses, List<ClusterAddress> readAddresses) {
                                                return new ClusterTopology(writeAddresses, readAddresses, readClusterVip);
                                            }
                                        });
                            }
                        }).subscribe(subscriber);
            }
        });
    }

    private Observable<List<ClusterAddress>> combine(final Observable<ChangeNotification<ClusterAddress>> topologyUpdates) {
        return Observable.create(new OnSubscribe<List<ClusterAddress>>() {
            @Override
            public void call(Subscriber<? super List<ClusterAddress>> subscriber) {
                final AtomicReference<List<ClusterAddress>> latestSnapshotRef = new AtomicReference<List<ClusterAddress>>(new ArrayList<ClusterAddress>());
                topologyUpdates.flatMap(new Func1<ChangeNotification<ClusterAddress>, Observable<List<ClusterAddress>>>() {
                    @Override
                    public Observable<List<ClusterAddress>> call(ChangeNotification<ClusterAddress> update) {
                        if (!update.isDataNotification()) {
                            return Observable.just(latestSnapshotRef.get());
                        }
                        List<ClusterAddress> addressList = new ArrayList<ClusterAddress>(latestSnapshotRef.get());
                        latestSnapshotRef.set(addressList);
                        ClusterAddress updatedAddress = update.getData();
                        switch (update.getKind()) {
                            case Add:
                            case Modify:
                                for (int i = 0; i < addressList.size(); i++) {
                                    if (addressList.get(i).equals(updatedAddress.getHostName())) {
                                        addressList.set(i, updatedAddress);
                                        return Observable.just(addressList);
                                    }
                                }
                                addressList.add(updatedAddress);
                                break;
                            case Delete:
                                for (int i = 0; i < addressList.size(); i++) {
                                    if (addressList.get(i).equals(updatedAddress.getHostName())) {
                                        addressList.remove(i);
                                        return Observable.just(addressList);
                                    }
                                }
                        }
                        return Observable.just(addressList);
                    }
                }).subscribe(subscriber);
            }
        });
    }

    private Set<String> resolveBootstrapServers(String bootstrapAddress) {
        if (!isDnsName()) {
            return Collections.singleton(bootstrapAddress);
        }
        Set<String> servers = new HashSet<>();
        try {
            for (ChangeNotification<String> serverNotification : DnsResolver.resolveServerDN(bootstrapAddress)) {
                servers.add(serverNotification.getData());
            }
        } catch (NamingException ignored) {
            throw new IllegalArgumentException("Cannot resolve bootstrap server address " + bootstrapAddress);
        }
        return servers;
    }

    private boolean isDnsName() {
        return bootstrapAddress.contains(".");
    }

    /**
     * Subscribe
     */
    private Observable<ServerType> resolveServerType() {
        int effectivePort;
        if (interestPort > 0) {
            effectivePort = interestPort;
        } else {
            effectivePort = detectOpenPort(12103, 12203);
            if (effectivePort <= 0) {
                return Observable.error(new IllegalArgumentException("Interest port not defined, and defaults are not open"));
            }
            interestPort = effectivePort;
        }
        return resolveServerType(effectivePort);
    }

    private int detectOpenPort(int... ports) {
        for (int port : ports) {
            ConnectivityVerifier verifier = new ConnectivityVerifier(resolvedBootstrapServers.get(0), port);
            try {
                if (verifier.isOpen(5, TimeUnit.SECONDS)) {
                    return port;
                }
            } catch (InterruptedException ignored) {
            }
        }
        return 0;
    }

    /**
     * Resolve type of the bootstrap server. Additionally if bootstrap server is write, resolve VIP of read cluster.
     */
    private Observable<ServerType> resolveServerType(final int interestPort) {
        System.out.println("Subscribing to bootstrap server " + bootstrapAddress + ':' + interestPort + "@vip=" + bootstrapVip + "...");
        final EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withClientId("serverTypeResolverClient")
                .withServerResolver(bootstrapResolver(interestPort))
                .withTransportConfig(transportConfig)
                .build();
        return interestClient
                .forInterest(Interests.forVips(Operator.Like, bootstrapVip))
                .buffer(2, TimeUnit.SECONDS) // TODO Change to buffer sentinels once they work as expected
                .compose(InterestFunctions.snapshots())
                .filter(new Func1<LinkedHashSet<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(LinkedHashSet<InstanceInfo> list) {
                        return !list.isEmpty();
                    }
                })
                .take(1)
                .flatMap(new Func1<LinkedHashSet<InstanceInfo>, Observable<ServerType>>() {
                    @Override
                    public Observable<ServerType> call(LinkedHashSet<InstanceInfo> instanceInfos) {
                        for (InstanceInfo instanceInfo : instanceInfos) {
                            if (isBootstrapServerInstance(instanceInfo, interestPort)) {
                                ServiceEndpoint registerEndpoint = REGISTRATION_SERVICE_SELECTOR.returnServiceEndpoint(instanceInfo);
                                serverType = registerEndpoint != null ? ServerType.Write : ServerType.Read;
                                bootstrapVip = instanceInfo.getVipAddress();
                                if (serverType == ServerType.Write) {
                                    ServiceEndpoint replicationEndpoint = REPLICATION_SERVICE_SELECTOR.returnServiceEndpoint(instanceInfo);
                                    resolvedClusterAddress = ClusterAddress.valueOf(
                                            bootstrapAddress,
                                            registerEndpoint.getServicePort().getPort(),
                                            interestPort,
                                            replicationEndpoint.getServicePort().getPort()
                                    );
                                } else {
                                    resolvedClusterAddress = ClusterAddress.readClusterAddressFrom(
                                            bootstrapAddress,
                                            interestPort
                                    );
                                }
                                System.out.printf("Server %s (application %s) matches bootstrap; the server type is %s\n", instanceInfo.getId(), instanceInfo.getApp(), serverType);
                                break;
                            }
                        }
                        // Try to discover read cluster VIP, if the bootstrap server is Write
                        if (readClusterVip == null && serverType == ServerType.Write) {
                            for (InstanceInfo instanceInfo : instanceInfos) {
////                                ServiceEndpoint registerEndpoint = REGISTRATION_SERVICE_SELECTOR.returnServiceEndpoint(instanceInfo);
////                                ServiceEndpoint interestEndpoint = INTEREST_SERVICE_SELECTOR.returnServiceEndpoint(instanceInfo);
//                                if (registerEndpoint == null && interestEndpoint != null) {
                                if (Pattern.matches(EUREKA_READ_VIP_PATTERN, instanceInfo.getApp())) {
                                    readClusterVip = instanceInfo.getVipAddress();
                                    System.out.printf("Server %s (application %s) with VIP %s matches read cluster node\n", instanceInfo.getId(), instanceInfo.getApp(), instanceInfo.getVipAddress());
                                    break;
                                }
                            }
                        }

                        if (serverType == null)

                        {
                            System.out.println("ERROR: Cannot resolve bootstrap server type");
                            return Observable.empty();
                        }

                        return Observable.just(serverType);
                    }
                })
                .timeout(30, TimeUnit.SECONDS)
                .doOnTerminate(new Action0() {
                    @Override
                    public void call() {
                        interestClient.shutdown();
                    }
                });
    }

    /**
     * Check if the given instance info object describes the bootstrap server.
     */
    private boolean isBootstrapServerInstance(InstanceInfo instanceInfo, int expectedInterestPort) {
        boolean matchedPort = false;
        for (ServicePort servicePort : instanceInfo.getPorts()) {
            if (servicePort.getPort().equals(expectedInterestPort)) {
                matchedPort = true;
                break;
            }
        }
        if (!matchedPort) {
            return false;
        }

        Set<String> myAddresses = new HashSet<>();
        // localhost is never added to IInstanceInfo, but as CLI and server run on the same host we can
        // take IPs from local NICs directly and do the matching.
        if ("localhost".equals(bootstrapAddress) || "127.0.0.1".equals(bootstrapAddress)) {
            myAddresses.add(SystemUtil.getHostName());
            myAddresses.addAll(SystemUtil.getPrivateIPs());
            myAddresses.addAll(SystemUtil.getPublicIPs());
        } else {
            myAddresses.addAll(resolvedBootstrapServers);
        }
        for (NetworkAddress networkAddress : instanceInfo.getDataCenterInfo().getAddresses()) {
            if (myAddresses.contains(networkAddress.getHostName())) {
                return true;
            }
            if (myAddresses.contains(networkAddress.getIpAddress())) {
                return true;
            }
        }
        return false;
    }

    private ServerResolver bootstrapResolver(int port) {
        final boolean isDnsName = isDnsName();
        if (isDnsName) {
            return ServerResolvers.fromDnsName(bootstrapAddress).withPort(port);
        }
        return ServerResolvers.fromHostname(bootstrapAddress).withPort(port);
    }

    private Observable<ChangeNotification<ClusterAddress>> resolveReadClusterFromEureka() {
        final EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withClientId("readClusterInterestClient")
                .withServerResolver(bootstrapResolver(interestPort))
                .withTransportConfig(transportConfig)
                .build();
        return interestClient.forInterest(Interests.forVips(readClusterVip))
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<ChangeNotification<ClusterAddress>>>() {
                    @Override
                    public Observable<ChangeNotification<ClusterAddress>> call(ChangeNotification<InstanceInfo> notification) {
                        if (!notification.isDataNotification()) {
                            return Observable.empty();
                        }
                        ClusterAddress clusterAddress;
                        ServiceEndpoint interestEndpoint = INTEREST_SERVICE_SELECTOR.returnServiceEndpoint(notification.getData());
                        if (interestEndpoint != null) {
                            clusterAddress = ClusterAddress.readClusterAddressFrom(interestEndpoint.getAddress().getHostName(), interestEndpoint.getServicePort().getPort());
                        } else {
                            // TODO Workaround for not working registration
                            InstanceInfo instanceInfo = notification.getData();
                            clusterAddress = ClusterAddress.readClusterAddressFrom(
                                    instanceInfo.getDataCenterInfo().getDefaultAddress().getHostName(),
                                    12203
                            );
                        }
                        switch (notification.getKind()) {
                            case Add:
                            case Modify:
                                return Observable.just(new ChangeNotification<ClusterAddress>(Kind.Add, clusterAddress));
                            case Delete:
                                return Observable.just(new ChangeNotification<ClusterAddress>(Kind.Delete, clusterAddress));
                        }
                        return Observable.error(new IllegalArgumentException("Unexpected notification type " + notification.getKind()));
                    }
                });
    }
}
