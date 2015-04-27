package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotifications;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.loadbalancer.RoundRobinLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A Server Resolver that uses an Ocelli loadbalancer for roundrobin loadbalancing. The {@link #resolve()} will
 * not emit any elements until the loadbalancer have warmed up for the first time. The loadbalancer warm up time
 * can be changed from defaults by calling {@link #withWarmUpConfiguration(int, TimeUnit)}.
 *
 * @author David Liu
 */
public class OcelliServerResolver implements ServerResolver {

    private static final Logger logger = LoggerFactory.getLogger(OcelliServerResolver.class);

    private final int warmUpTimeout;
    private final TimeUnit timeUnit;

    private final Observable<ChangeNotification<Server>> serverSource;
    private final LoadBalancer<Server> loadBalancer;


    protected OcelliServerResolver(Server... servers) {
        this(sourceFromList(servers), RoundRobinLoadBalancer.<Server>create(new Random().nextInt(100)), 10, TimeUnit.SECONDS);
    }

    protected OcelliServerResolver(Observable<ChangeNotification<Server>> serverSource) {
        this(serverSource, RoundRobinLoadBalancer.<Server>create(new Random().nextInt(100)), 10, TimeUnit.SECONDS);
    }

    protected OcelliServerResolver(Observable<ChangeNotification<Server>> serverSource, LoadBalancer<Server> loadBalancer, int warmUpTimeout, TimeUnit timeUnit) {
        this.serverSource = serverSource;
        this.warmUpTimeout = warmUpTimeout;
        this.timeUnit = timeUnit;
        this.loadBalancer = loadBalancer;
    }

    public OcelliServerResolver withWarmUpConfiguration(int newWarmUpTimeout, TimeUnit newTimeUnit) {
        return new OcelliServerResolver(serverSource, loadBalancer, newWarmUpTimeout, newTimeUnit);
    }

    public OcelliServerResolver withLoadBalancer(LoadBalancer<Server> newLoadBalancer) {
        return new OcelliServerResolver(serverSource, newLoadBalancer, warmUpTimeout, timeUnit);
    }

    @Override
    public void close() {
        // no need to close the Ocelli LB
    }

    @Override
    public Observable<Server> resolve() {
        return connectLoadBalancer().concatMap(new Func1<LoadBalancer<Server>, Observable<Server>>() {
            @Override
            public Observable<Server> call(LoadBalancer<Server> loadBalancer) {
                return loadBalancer;  // lb guarantees return of only 1
            }
        });
    }

    /**
     * Connect and warm up the load balancer to the server source. onComplete the result Observable once the
     * load balancer has warmed up.
     */
    private Observable<LoadBalancer<Server>> connectLoadBalancer() {
        return serverSource
                .compose(ChangeNotifications.<Server>buffers())
                .compose(ChangeNotifications.<Server>snapshots())
                .materialize()
                .concatMap(new Func1<Notification<LinkedHashSet<Server>>, Observable<? extends LoadBalancer<Server>>>() {
                    @Override
                    public Observable<? extends LoadBalancer<Server>> call(Notification<LinkedHashSet<Server>> rxNotification) {
                        switch (rxNotification.getKind()) {
                            case OnNext:
                                LinkedHashSet<Server> servers = rxNotification.getValue();
                                if (servers.isEmpty()) {  // if onNext is empty, do nothing and wait
                                    return Observable.never();
                                } else {
                                    logger.info("Populating the loadbalancer with {} servers", servers.size());
                                    loadBalancer.call(new ArrayList<>(servers));
                                }
                                break;
                            case OnCompleted:  // onCompleted also return the loadbalancer
                                break;
                            case OnError:
                                return Observable.error(rxNotification.getThrowable());
                        }
                        return Observable.just(loadBalancer);
                    }
                })
                .timeout(warmUpTimeout, timeUnit)
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends LoadBalancer<Server>>>() {
                    @Override
                    public Observable<? extends LoadBalancer<Server>> call(Throwable throwable) {
                        if (!(throwable instanceof TimeoutException)) {
                            logger.warn("Exception thrown when connecting serverSource to load balancer, using backup values", throwable);
                        }
                        return Observable.just(loadBalancer);
                    }
                })
                .take(1);
    }

    private static Observable<ChangeNotification<Server>> sourceFromList(Server... servers) {
        return Observable.from(servers)
                .map(new Func1<Server, ChangeNotification<Server>>() {
                    @Override
                    public ChangeNotification<Server> call(Server server) {
                        return new ChangeNotification<>(ChangeNotification.Kind.Add, server);
                    }
                });
    }

}
