package com.netflix.eureka2.server.resolver;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.host.DnsChangeNotificationSource;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public abstract class DnsEurekaEndpointResolver implements EurekaEndpointResolver {

    private static final long RELOAD_INTERVAL_MS = 5 * 60 * 1000;

    private static final ChangeNotification<EurekaEndpoint> BUFFER_SENTINEL_NOTIFICATION = ChangeNotification.bufferSentinel();

    private final DnsChangeNotificationSource dnsChangeNotificationSource;

    protected DnsEurekaEndpointResolver(String domainName, Scheduler scheduler) {
        this.dnsChangeNotificationSource = new DnsChangeNotificationSource(domainName, RELOAD_INTERVAL_MS, 0, TimeUnit.MILLISECONDS, scheduler);
    }

    @Override
    public Observable<ChangeNotification<EurekaEndpoint>> eurekaEndpoints() {
        return dnsChangeNotificationSource.forInterest(null).map(
                new Func1<ChangeNotification<String>, ChangeNotification<EurekaEndpoint>>() {
                    @Override
                    public ChangeNotification<EurekaEndpoint> call(ChangeNotification<String> hostNotification) {
                        Kind kind = hostNotification.getKind();
                        if (kind == Kind.BufferSentinel) {
                            return BUFFER_SENTINEL_NOTIFICATION;
                        }
                        EurekaEndpoint endpoint = createEndpoint(hostNotification.getData());
                        if (kind == Kind.Add || kind == Kind.Modify) {
                            return new ChangeNotification<EurekaEndpoint>(Kind.Add, endpoint);
                        }
                        return new ChangeNotification<EurekaEndpoint>(Kind.Delete, endpoint);
                    }
                });
    }

    protected abstract EurekaEndpoint createEndpoint(String hostName);

    public static DnsEurekaEndpointResolver writeServerDnsResolver(final String domainName,
                                                                   final int registrationPort,
                                                                   final int interestPort,
                                                                   final int replicationPort,
                                                                   Scheduler scheduler) {
        return new DnsEurekaEndpointResolver(domainName, scheduler) {
            @Override
            protected EurekaEndpoint createEndpoint(String hostName) {
                return EurekaEndpoint.writeServerEndpointFrom(domainName, registrationPort, interestPort, replicationPort);
            }
        };
    }

    public static DnsEurekaEndpointResolver readServerDnsResolver(final String domainName, final int interestPort, Scheduler scheduler) {
        return new DnsEurekaEndpointResolver(domainName, scheduler) {
            @Override
            protected EurekaEndpoint createEndpoint(String hostName) {
                return EurekaEndpoint.readServerEndpointFrom(domainName, interestPort);
            }
        };
    }
}
