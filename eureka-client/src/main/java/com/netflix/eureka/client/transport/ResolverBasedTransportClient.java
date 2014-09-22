package com.netflix.eureka.client.transport;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.netflix.client.DefaultLoadBalancerRetryHandler;
import com.netflix.client.RetryHandler;
import com.netflix.client.config.DefaultClientConfigImpl;
import com.netflix.client.config.IClientConfig;
import com.netflix.eureka.client.ServerResolver;
import com.netflix.eureka.client.ServerResolver.ServerEntry;
import com.netflix.eureka.client.transport.tcp.TcpServerConnection;
import com.netflix.eureka.transport.base.BaseMessageBroker;
import com.netflix.loadbalancer.DummyPing;
import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.netflix.ribbon.transport.netty.RibbonTransport;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Convenience base implementation for {@link TransportClient} that reads the server list from a {@link ServerResolver}
 *
 * @author Nitesh Kant
 */
public abstract class ResolverBasedTransportClient<A extends SocketAddress> implements TransportClient {

    private static final Logger logger = LoggerFactory.getLogger(ResolverBasedTransportClient.class);

    private static final IPing DUMMY_PING = new DummyPing();

    private final ServerResolver<A> resolver;
    protected final IClientConfig clientConfig;
    private final PipelineConfigurator<Object, Object> pipelineConfigurator;
    protected final ZoneAwareLoadBalancer<Server> loadBalancer;

    private RxClient<Object, Object> tcpClient;

    protected ResolverBasedTransportClient(ServerResolver<A> resolver,
                                           IClientConfig clientConfig,
                                           PipelineConfigurator<Object, Object> pipelineConfigurator) {
        this.resolver = resolver;
        this.clientConfig = clientConfig;
        this.pipelineConfigurator = pipelineConfigurator;
        ServerList<Server> serverList = new ResolverServerList(resolver);
        loadBalancer = new ZoneAwareLoadBalancer<>(clientConfig, new RoundRobinRule(), DUMMY_PING, serverList, null /* filter */);
    }

    @Override
    public Observable<ServerConnection> connect() {
        // TODO: look into this later once we know more about possible transport errors.
        RetryHandler retryHandler = new DefaultLoadBalancerRetryHandler(0, 1, true);
        tcpClient = RibbonTransport.newTcpClient(loadBalancer, pipelineConfigurator, clientConfig, retryHandler);
        Observable<ServerConnection> connection = tcpClient.connect()
                .take(1)
                .map(new Func1<ObservableConnection<Object, Object>, ServerConnection>() {
                    @Override
                    public ServerConnection call(ObservableConnection<Object, Object> connection) {
                        return new TcpServerConnection(new BaseMessageBroker(connection));
                    }
                });
        return Observable.concat(
                resolver.resolve().take(1).ignoreElements().cast(ServerConnection.class), // Load balancer will through exception if no servers in pool
                connection
        );
    }

    @Override
    public void shutdown() {
        if (tcpClient != null) {
            tcpClient.shutdown();
        }
    }

    protected abstract boolean matches(ServerEntry<A> entry);

    protected abstract int defaultPort();

    protected static IClientConfig getClientConfig(String name) {
        // TODO: figure out what we want to configure, and how to provide this configuration information.
        DefaultClientConfigImpl config = new DefaultClientConfigImpl("eureka.");
        config.setClientName(name);
        return config;
    }

    class ResolverServerList implements ServerList<Server> {

        private final CopyOnWriteArrayList<ServerEntry<A>> servers = new CopyOnWriteArrayList<>();

        ResolverServerList(ServerResolver<A> resolver) {
            resolver.resolve()
                    .subscribe(new Action1<ServerEntry<A>>() {
                        @Override
                        public void call(ServerResolver.ServerEntry<A> entry) {
                            if (matches(entry)) {
                                switch (entry.getAction()) {
                                    case Add:
                                        servers.add(entry);
                                        break;
                                    case Remove:
                                        servers.remove(entry);
                                        break;
                                }
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

        @Override
        public List<Server> getInitialListOfServers() {
            return getUpdatedListOfServers();
        }

        @Override
        public List<Server> getUpdatedListOfServers() {
            List<Server> newList = new ArrayList<>(servers.size());
            for (ServerEntry<A> entry : servers) {
                InetSocketAddress address = (InetSocketAddress) entry.getServer();
                newList.add(new Server(address.getHostName(), address.getPort()));
            }
            return newList;
        }
    }
}
