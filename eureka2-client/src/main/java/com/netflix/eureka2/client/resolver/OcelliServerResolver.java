package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.functions.ChangeNotificationFunctions;
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
 * A Server Resolver that wraps an Ocelli loadbalancer around an internal ServerResolver. The {@link #resolve()} will
 * not emit any elements until the loadbalancer have warmed up for the first time.
 *
 * Note that even though this class keeps track of state in an AtomicRef, it is NOT thread safe for close()
 *
 * @author David Liu
 */
class OcelliServerResolver extends ServerResolver {

    private static final Logger logger = LoggerFactory.getLogger(OcelliServerResolver.class);

    private final int warmUpTimeout;
    private final TimeUnit timeUnit;
    private final ServerResolver delegateResolver;
    private final LoadBalancer<Server> loadBalancer;

    OcelliServerResolver(ServerResolver delegateResolver) {
        this(delegateResolver, 5, TimeUnit.SECONDS);
    }

    OcelliServerResolver(ServerResolver delegateResolver, int warmUpTimeout, TimeUnit timeUnit) {
        super(delegateResolver.serverSource());
        this.warmUpTimeout = warmUpTimeout;
        this.timeUnit = timeUnit;
        this.delegateResolver = delegateResolver;
        this.loadBalancer = RoundRobinLoadBalancer.create();
    }

    @Override
    public void close() {
        delegateResolver.close();
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
        return serverSource()
                .compose(ChangeNotificationFunctions.<Server>buffers())
                .compose(ChangeNotificationFunctions.<Server>snapshots())
                .map(new Func1<LinkedHashSet<Server>, LoadBalancer<Server>>() {
                    @Override
                    public LoadBalancer<Server> call(LinkedHashSet<Server> servers) {
                        if (servers != null) {
                            loadBalancer.call(new ArrayList<>(servers));
                        }
                        return loadBalancer;
                    }
                })
                .timeout(warmUpTimeout, timeUnit)
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends LoadBalancer<Server>>>() {
                    @Override
                    public Observable<? extends LoadBalancer<Server>> call(Throwable throwable) {
                        if (!(throwable instanceof TimeoutException)) {
                            logger.warn("Exception thrown when connecting serverSource to load balancer", throwable);
                        }

                        return Observable.just(loadBalancer);
                    }
                })
                .take(1);
    }
}
