package com.netflix.eureka.client.transport;

import com.netflix.eureka.client.ServerResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action1;

import java.net.SocketAddress;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Convenience base implementation for {@link TransportClient} that reads the server list from a {@link ServerResolver}
 *
 * @author Nitesh Kant
 */
public abstract class ResolverBasedTransportClient<A extends SocketAddress> implements TransportClient {

    private static final Logger logger = LoggerFactory.getLogger(ResolverBasedTransportClient.class);

    protected final CopyOnWriteArrayList<A> servers;

    protected ResolverBasedTransportClient(ServerResolver<A> resolver) {
        servers = new CopyOnWriteArrayList<A>();
        resolver.resolve()
                .subscribe(new Action1<ServerResolver.ServerEntry<A>>() {
                    @Override
                    public void call(ServerResolver.ServerEntry<A> entry) {
                        switch (entry.getAction()) {
                            case Add:
                                servers.add(entry.getServer());
                                break;
                            case Remove:
                                servers.remove(entry.getServer());
                                break;
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        logger.error("Server resolver sent an error. This means the server list is not going to be updated. " +
                                     "Current server list: " + servers, throwable);
                    }
                });
    }
}
