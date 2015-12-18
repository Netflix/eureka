package com.netflix.eureka2.server.resolver;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.interests.host.DnsChangeNotificationSource;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
abstract class DnsEurekaClusterResolver implements EurekaClusterResolver {

    private static final long RELOAD_INTERVAL_MS = 5 * 60 * 1000;

    private static final ChangeNotification<ClusterAddress> BUFFER_SENTINEL_NOTIFICATION = ChangeNotification.bufferSentinel();

    private final Observable<ChangeNotification<String>> dnsChangeNotificationSource;

    protected DnsEurekaClusterResolver(String domainName, Scheduler scheduler) {
        this.dnsChangeNotificationSource = createDnsChangeNotificationSource(domainName, scheduler);
    }

    @Override
    public Observable<ChangeNotification<ClusterAddress>> clusterTopologyChanges() {
        return dnsChangeNotificationSource.map(
                new Func1<ChangeNotification<String>, ChangeNotification<ClusterAddress>>() {
                    @Override
                    public ChangeNotification<ClusterAddress> call(ChangeNotification<String> hostNotification) {
                        Kind kind = hostNotification.getKind();
                        if (kind == Kind.BufferSentinel) {
                            return BUFFER_SENTINEL_NOTIFICATION;
                        }
                        ClusterAddress address = createClusterAddress(hostNotification.getData());
                        if (kind == Kind.Add || kind == Kind.Modify) {
                            return new ChangeNotification<>(Kind.Add, address);
                        }
                        return new ChangeNotification<>(Kind.Delete, address);
                    }
                });
    }

    protected Observable<ChangeNotification<String>> createDnsChangeNotificationSource(String domainName, Scheduler scheduler) {
        return new DnsChangeNotificationSource(domainName, RELOAD_INTERVAL_MS, 0, TimeUnit.MILLISECONDS, scheduler).forInterest(null);
    }

    protected abstract ClusterAddress createClusterAddress(String hostName);

    static class DnsWriteServerClusterResolver extends DnsEurekaClusterResolver {

        private final int registrationPort;
        private final int interestPort;
        private final int replicationPort;

        DnsWriteServerClusterResolver(String domainName,
                                      int registrationPort,
                                      int interestPort,
                                      int replicationPort,
                                      Scheduler scheduler) {
            super(domainName, scheduler);
            this.registrationPort = registrationPort;
            this.interestPort = registrationPort;
            this.replicationPort = registrationPort;
        }

        @Override
        protected ClusterAddress createClusterAddress(String hostName) {
            return ClusterAddress.valueOf(hostName, registrationPort, interestPort, replicationPort);
        }
    }

    static class DnsReadServerClusterResolver extends DnsEurekaClusterResolver {

        private final int interestPort;

        DnsReadServerClusterResolver(String domainName,
                                     int interestPort,
                                     Scheduler scheduler) {
            super(domainName, scheduler);
            this.interestPort = interestPort;
        }

        @Override
        protected ClusterAddress createClusterAddress(String hostName) {
            return ClusterAddress.readClusterAddressFrom(hostName, interestPort);
        }
    }
}
