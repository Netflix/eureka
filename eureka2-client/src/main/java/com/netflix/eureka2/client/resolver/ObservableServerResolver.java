package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.client.functions.ChangeNotificationFunctions;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Return a ServerResolver that (best effort) round-robin resolves from a stream of servers, batching based
 * on stream hints and/or timeout.
 *
 * @author David Liu
 */
class ObservableServerResolver extends ServerResolver {

    private final int warmUpTimeout;
    private final TimeUnit timeUnit;
    private final AtomicLong count = new AtomicLong(0);

    ObservableServerResolver(Observable<ChangeNotification<Server>> serverSource) {
        this(serverSource, 5, TimeUnit.SECONDS);
    }

    ObservableServerResolver(Observable<ChangeNotification<Server>> serverSource, int warmUpTimeout, TimeUnit timeUnit) {
        super(serverSource);
        this.warmUpTimeout = warmUpTimeout;
        this.timeUnit = timeUnit;
    }

    @Override
    public void close() {
        count.set(0);
    }

    @Override
    public Observable<Server> resolve() {
        return serverSource()
                .compose(ChangeNotificationFunctions.<Server>buffers())
                .compose(ChangeNotificationFunctions.<Server>snapshots())
                .map(new Func1<LinkedHashSet<Server>, List<Server>>() {
                    @Override
                    public List<Server> call(LinkedHashSet<Server> servers) {
                        if (!servers.isEmpty()) {
                            return new ArrayList<>(servers);
                        }
                        return null;
                    }
                })
                .filter(RxFunctions.filterNullValuesFunc())
                .timeout(warmUpTimeout, timeUnit)
                .onErrorResumeNext(new Func1<Throwable, Observable<? extends List<Server>>>() {
                    @Override
                    public Observable<? extends List<Server>> call(Throwable throwable) {
                        if (throwable instanceof TimeoutException) {
                            return Observable.empty();
                        }
                        return Observable.error(throwable);
                    }
                })
                .map(new Func1<List<Server>, Server>() {
                    @Override
                    public Server call(List<Server> servers) {
                        int pos = (int) (count.getAndIncrement() % servers.size());
                        return servers.get(pos);
                    }
                })
                .take(1);
    }
}
