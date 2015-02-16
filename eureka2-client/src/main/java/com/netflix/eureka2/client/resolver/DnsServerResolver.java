package com.netflix.eureka2.client.resolver;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotificationSource;
import com.netflix.eureka2.interests.host.DnsChangeNotificationSource;
import com.netflix.eureka2.Server;
import netflix.ocelli.LoadBalancerBuilder;
import netflix.ocelli.loadbalancer.DefaultLoadBalancerBuilder;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class DnsServerResolver extends AbstractServerResolver {

    private final ChangeNotificationSource<String> dnsChangeNotificationSource;
    private final int port;

    public DnsServerResolver(ChangeNotificationSource<String> dnsChangeNotificationSource, int port,
                             LoadBalancerBuilder<Server> loadBalancerBuilder) {
        super(loadBalancerBuilder);
        this.dnsChangeNotificationSource = dnsChangeNotificationSource;
        this.port = port;
    }

    @Override
    protected Observable<ChangeNotification<Server>> serverUpdates() {
        return dnsChangeNotificationSource.forInterest(null).map(new Func1<ChangeNotification<String>, ChangeNotification<Server>>() {
            @Override
            public ChangeNotification<Server> call(ChangeNotification<String> notification) {
                // Modify kind not supported
                switch (notification.getKind()) {
                    case Add:
                        return new ChangeNotification<Server>(Kind.Add, new Server(notification.getData(), port));
                    case Delete:
                        return new ChangeNotification<Server>(Kind.Delete, new Server(notification.getData(), port));
                }
                return null;
            }
        });
    }

    public static class DnsServerResolverBuilder {
        private String domainName;
        private int port;
        private long reloadInterval = -1;
        private long idleTimeout = -1;
        private TimeUnit reloadUnit = TimeUnit.MILLISECONDS;
        private LoadBalancerBuilder<Server> loadBalancerBuilder;
        private Scheduler scheduler;

        public DnsServerResolverBuilder withDomainName(String domainName) {
            this.domainName = domainName;
            return this;
        }

        public DnsServerResolverBuilder withPort(int port) {
            this.port = port;
            return this;
        }

        public DnsServerResolverBuilder withReloadInterval(long reloadInterval) {
            this.reloadInterval = reloadInterval;
            return this;
        }

        public DnsServerResolverBuilder withIdleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        public DnsServerResolverBuilder withReloadUnit(TimeUnit reloadUnit) {
            this.reloadUnit = reloadUnit;
            return this;
        }

        public DnsServerResolverBuilder withLoadBalancerBuilder(LoadBalancerBuilder<Server> loadBalancerBuilder) {
            this.loadBalancerBuilder = loadBalancerBuilder;
            return this;
        }

        public DnsServerResolverBuilder withScheduler(Scheduler scheduler) {
            this.scheduler = scheduler;
            return this;
        }

        public DnsServerResolver build() {
            if (domainName == null) {
                throw new IllegalStateException("Domain name not set");
            }
            if (port == 0) {
                throw new IllegalStateException("Port number not set");
            }
            if (loadBalancerBuilder == null) {
                loadBalancerBuilder = new DefaultLoadBalancerBuilder<>(null);
            }
            if(scheduler == null) {
                scheduler = Schedulers.computation();
            }
            return new DnsServerResolver(createDnsChangeNotificationSource(), port, loadBalancerBuilder);
        }

        protected ChangeNotificationSource<String> createDnsChangeNotificationSource() {
            return new DnsChangeNotificationSource(
                    domainName,
                    reloadInterval < 0 ? DnsChangeNotificationSource.DNS_LOOKUP_INTERVAL : reloadInterval,
                    idleTimeout < 0 ? DnsChangeNotificationSource.IDLE_TIMEOUT : idleTimeout,
                    reloadUnit,
                    scheduler
            );
        }
    }
}
