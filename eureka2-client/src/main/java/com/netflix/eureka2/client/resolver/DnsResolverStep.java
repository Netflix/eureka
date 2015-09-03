package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.interests.host.DnsResolver;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import java.util.Set;

/**
 * @author David Liu
 */
public class DnsResolverStep implements HostResolverStep {

    private final String dnsName;
    private final Scheduler dnsLoadScheduler;

    DnsResolverStep(String dnsName) {
        this.dnsName = dnsName;
        this.dnsLoadScheduler = Schedulers.io();
    }

    /* visible for testing */ DnsResolverStep configureDnsNameSource(final Observable<ChangeNotification<String>> changeNotificationSource) {
        return new DnsResolverStep(dnsName) {
            @Override
            protected Observable<ChangeNotification<String>> createDnsChangeNotificationSource() {
                return changeNotificationSource;
            }
        };
    }

    protected Observable<ChangeNotification<String>> createDnsChangeNotificationSource() {
        return Observable.create(new Observable.OnSubscribe<ChangeNotification<String>>() {
            @Override
            public void call(Subscriber<? super ChangeNotification<String>> subscriber) {
                try {
                    Set<ChangeNotification<String>> names = DnsResolver.resolveServerDN(dnsName);
                    Observable.from(names).subscribe(subscriber);
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        }).subscribeOn(dnsLoadScheduler);   // async subscribe on an io scheduler
    }

    @Override
    public ServerResolver withPort(final int port) {
        final Observable<ChangeNotification<String>> dnsChangeNotificationSource = createDnsChangeNotificationSource();

        final Observable<ChangeNotification<Server>> serverSource = dnsChangeNotificationSource
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

        ServerResolver resolver = new RoundRobinServerResolver(serverSource);

        return resolver;
    }
}
