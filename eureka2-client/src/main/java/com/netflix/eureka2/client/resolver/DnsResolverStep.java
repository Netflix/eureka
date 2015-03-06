package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotificationSource;
import com.netflix.eureka2.interests.host.DnsChangeNotificationSource;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class DnsResolverStep implements HostResolverStep {

    private final String dnsName;
    private final Configuration configuration;

    DnsResolverStep(String dnsName) {
        this(dnsName, new Configuration());
    }

    DnsResolverStep(String dnsName, Configuration configuration) {
        this.dnsName = dnsName;
        this.configuration = configuration;
    }

    public DnsResolverStep configureReload(int reloadInterval, int idleTimeout, TimeUnit timeUnit) {
        Configuration updatedConfig = configuration
                .copy()
                .withReloadInterval(reloadInterval)
                .withIdleTimeout(idleTimeout)
                .withTimeUnit(timeUnit);

        return new DnsResolverStep(dnsName, updatedConfig);
    }

    public DnsResolverStep configureReloadScheduler(Scheduler scheduler) {
        Configuration updatedConfig = configuration
                .copy()
                .withScheduler(scheduler);

        return new DnsResolverStep(dnsName, updatedConfig);
    }

    /* visible for testing */ DnsResolverStep configureChangeNotificationSource(final ChangeNotificationSource<String> changeNotificationSource) {
        return new DnsResolverStep(dnsName, configuration) {
            @Override
            protected ChangeNotificationSource<String> createDnsChangeNotificationSource() {
                return changeNotificationSource;
            }
        };
    }


    protected ChangeNotificationSource<String> createDnsChangeNotificationSource() {
        return new DnsChangeNotificationSource(
                dnsName,
                configuration.reloadInterval,
                configuration.idleTimeout,
                configuration.timeUnit,
                configuration.scheduler
        );
    }

    @Override
    public ServerResolver withPort(final int port) {
        final ChangeNotificationSource<String> dnsChangeNotificationSource = createDnsChangeNotificationSource();

        final Observable<ChangeNotification<Server>> serverSource =
                dnsChangeNotificationSource.forInterest(null)
                        .map(new Func1<ChangeNotification<String>, ChangeNotification<Server>>() {
                            @Override
                            public ChangeNotification<Server> call(ChangeNotification<String> notification) {
                                switch (notification.getKind()) {
                                    case BufferSentinel:
                                        return ChangeNotification.bufferSentinel();  // type change
                                    case Add:
                                        return new ChangeNotification<>(Kind.Add, new Server(notification.getData(), port));
                                    case Delete:
                                        return new ChangeNotification<>(Kind.Delete, new Server(notification.getData(), port));
                                    default:
                                        // do nothing. This include Type.Modify, which is not supported for dns
                                }
                                return null;
                            }
                        })
                        .filter(new Func1<ChangeNotification<Server>, Boolean>() {
                            @Override
                            public Boolean call(ChangeNotification<Server> notification) {
                                return notification != null;
                            }
                        });

        return new ObservableServerResolver(serverSource);
    }


    protected static class Configuration {
        // mutable fields set to defaults
        long reloadInterval = DnsChangeNotificationSource.DNS_LOOKUP_INTERVAL;
        long idleTimeout = DnsChangeNotificationSource.IDLE_TIMEOUT;
        TimeUnit timeUnit = TimeUnit.MILLISECONDS;
        Scheduler scheduler = Schedulers.computation();

        public Configuration copy() {
            return new Configuration()
                    .withReloadInterval(this.reloadInterval)
                    .withIdleTimeout(this.idleTimeout)
                    .withTimeUnit(this.timeUnit)
                    .withScheduler(this.scheduler);
        }

        public Configuration withReloadInterval(long reloadInterval) {
            this.reloadInterval = reloadInterval;
            return this;
        }

        public Configuration withIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public Configuration withTimeUnit(TimeUnit reloadUnit) {
            this.timeUnit = reloadUnit;
            return this;
        }

        public Configuration withScheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }
    }
}
