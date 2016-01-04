package com.netflix.eureka2.server.resolver;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.host.DnsChangeNotificationSource;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
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

    static class DnsEurekaServerClusterResolver extends DnsEurekaClusterResolver {

        private final int serverPort;

        DnsEurekaServerClusterResolver(String domainName,
                                       int serverPort,
                                       Scheduler scheduler) {
            super(domainName, scheduler);
            this.serverPort = serverPort;
        }

        @Override
        protected ClusterAddress createClusterAddress(String hostName) {
            return ClusterAddress.valueOf(hostName, serverPort);
        }
    }
}
