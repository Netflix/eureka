package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import rx.Observable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Return a ServerResolver that round-robin resolves from a fixed list of servers.
 *
 * @author David Liu
 */
class StaticListServerResolver extends ServerResolver {

    private final List<Server> serverList;
    private final AtomicLong count = new AtomicLong(0);

    StaticListServerResolver(Server... servers) {
        super(servers);
        serverList = Arrays.asList(servers);
    }

    @Override
    public void close() {
    }

    @Override
    public Observable<Server> resolve() {
        if (serverList.isEmpty()) {
            return Observable.empty();
        }

        int pos = (int) (count.getAndIncrement() % serverList.size());
        return Observable.just(serverList.get(pos));
    }
}
