package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.functions.ChangeNotificationFunctions;
import com.netflix.eureka2.interests.ChangeNotification;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.loadbalancer.RoundRobinLoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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


    public OcelliServerResolver(Server... servers) {
        this(sourceFromList(servers), 5, TimeUnit.SECONDS);
    }

    public OcelliServerResolver(Observable<ChangeNotification<Server>> serverSource) {
        this(serverSource, 5, TimeUnit.SECONDS);
    }

    private OcelliServerResolver(Observable<ChangeNotification<Server>> serverSource, int warmUpTimeout, TimeUnit timeUnit) {
        this.serverSource = serverSource;
        this.warmUpTimeout = warmUpTimeout;
        this.timeUnit = timeUnit;
        this.loadBalancer = RoundRobinLoadBalancer.create();
    }

    public OcelliServerResolver withWarmUpConfiguration(int warmUpTimeout, TimeUnit timeUnit) {
        return new OcelliServerResolver(serverSource, warmUpTimeout, timeUnit);
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
                .compose(ChangeNotificationFunctions.<Server>buffers())
                .compose(ChangeNotificationFunctions.<Server>snapshots())
                .timeout(warmUpTimeout, timeUnit)
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends LinkedHashSet<Server>>>() {
                    @Override
                    public Observable<? extends LinkedHashSet<Server>> call(Throwable throwable) {
                        if (!(throwable instanceof TimeoutException)) {
                            logger.warn("Exception thrown when connecting serverSource to load balancer", throwable);
                        }
                        return Observable.just(EMPTY_SERVER_SET);
                    }
                })
                .map(new Func1<LinkedHashSet<Server>, LoadBalancer<Server>>() {
                    @Override
                    public LoadBalancer<Server> call(LinkedHashSet<Server> servers) {
                        if (!servers.isEmpty()) {
                            loadBalancer.call(new ArrayList<>(servers));
                        }
                        return loadBalancer;
                    }
                })
                .take(1);
    }

    private static LinkedHashSet<Server> EMPTY_SERVER_SET = new LinkedHashSet<>();

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
