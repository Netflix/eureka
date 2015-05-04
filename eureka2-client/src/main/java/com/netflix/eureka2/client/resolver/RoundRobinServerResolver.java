package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A Server Resolver that uses a round-robin strategy to select between servers. The {@link #resolve()} will
 * not emit any elements until the resolver have warmed up for the first time, i.e. the resolver has been loaded
 * with an initial list of servers. The warm up time can be changed from defaults by calling
 * {@link #withWarmUpConfiguration(int, TimeUnit)}.
 *
 * @author David Liu
 */
public class RoundRobinServerResolver implements ServerResolver {

    private static final Logger logger = LoggerFactory.getLogger(RoundRobinServerResolver.class);

    private static final Exception SERVER_CACHE_EMPTY_EXCEPTION = new NoSuchElementException("No servers available for this resolver");

    private final int cacheRefreshTimeout;
    private final TimeUnit timeUnit;

    private final Observable<ChangeNotification<Server>> serverSource;
    private final AtomicReference<List<Server>> serverCacheRef;
    private final AtomicInteger positionRef;

    protected RoundRobinServerResolver(Server... servers) {
        this(Observable.from(servers).map(ChangeNotifications.<Server>toAddChangeNotification()), 10, TimeUnit.SECONDS);
    }

    protected RoundRobinServerResolver(Observable<ChangeNotification<Server>> serverSource) {
        this(serverSource, 10, TimeUnit.SECONDS);
    }

    protected RoundRobinServerResolver(Observable<ChangeNotification<Server>> serverSource, int cacheRefreshTimeout, TimeUnit timeUnit) {
        this.serverSource = serverSource;
        this.cacheRefreshTimeout = cacheRefreshTimeout;
        this.timeUnit = timeUnit;
        this.serverCacheRef = new AtomicReference<List<Server>>(new ArrayList<Server>());
        this.positionRef = new AtomicInteger(new Random().nextInt(1000));
    }

    public RoundRobinServerResolver withWarmUpConfiguration(int newWarmUpTimeout, TimeUnit newTimeUnit) {
        return new RoundRobinServerResolver(serverSource, newWarmUpTimeout, newTimeUnit);
    }

    @Override
    public void close() {
        serverCacheRef.get().clear();
    }

    @Override
    public Observable<Server> resolve() {
        return refreshServerCache().concatMap(new Func1<List<Server>, Observable<? extends Server>>() {
            @Override
            public Observable<? extends Server> call(List<Server> servers) {
                if (servers.isEmpty()) {
                    return Observable.error(SERVER_CACHE_EMPTY_EXCEPTION);
                }

                int currentPos = Math.abs(positionRef.getAndIncrement());
                Server toReturn = servers.get(currentPos % servers.size());
                return Observable.just(toReturn);
            }
        });
    }

    /**
     * Connect and warm up the serverCache to the server source. onComplete the result Observable once the
     * serverCache has warmed up.
     */
    private Observable<List<Server>> refreshServerCache() {
        return serverSource
                .compose(ChangeNotifications.<Server>buffers())
                .compose(ChangeNotifications.<Server>snapshots())
                .materialize()
                .concatMap(new Func1<Notification<LinkedHashSet<Server>>, Observable<? extends List<Server>>>() {
                    @Override
                    public Observable<? extends List<Server>> call(Notification<LinkedHashSet<Server>> rxNotification) {
                        switch (rxNotification.getKind()) {
                            case OnNext:
                                LinkedHashSet<Server> servers = rxNotification.getValue();
                                if (servers.isEmpty()) {  // if onNext is empty, do nothing and wait
                                    return Observable.never();
                                } else {
                                    logger.info("Populating the serverCache with {} servers", servers.size());

                                    List<Server> newServerCache = new ArrayList<>(servers);
                                    // do a sort so the list is always roughly in the same order and therefore
                                    // comparable enough to the previous iteration aside from changes in elements
                                    Collections.sort(newServerCache);
                                    serverCacheRef.set(newServerCache);
                                }
                                break;
                            case OnCompleted:  // onCompleted also return the current serverCache
                                break;
                            case OnError:
                                return Observable.error(rxNotification.getThrowable());
                        }
                        return Observable.just(serverCacheRef.get());
                    }
                })
                .timeout(cacheRefreshTimeout, timeUnit)
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends List<Server>>>() {
                    @Override
                    public Observable<? extends List<Server>> call(Throwable throwable) {
                        if (!(throwable instanceof TimeoutException)) {
                            logger.warn("Exception thrown when connecting the serverCache to the serverSource, using backup values", throwable);
                        }
                        return Observable.just(serverCacheRef.get());
                    }
                })
                .take(1);
    }
}
