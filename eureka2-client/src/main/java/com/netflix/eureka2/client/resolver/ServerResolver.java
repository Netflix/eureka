package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import rx.Observable;
import rx.functions.Func1;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * A mechanism to discovery eureka servers. Each call to {@link #resolve()} returns an
 * observable with no more than one element the is the best contender for the next
 * connection.
 * <p>
 *
 * <h1>ServerResolver DSL</h1>
 * This class privides a DLS for creating various types of ServerResolvers.
 * <p>
 *
 * <h1>Thread safety</h1>
 * Calls to {@link #resolve} method are not thread safe, as given its embedded load balancing
 * semantic sharing single resolver by multiple clients has little sense.
 *
 * @author David Liu
 */
public abstract class ServerResolver {

    /**
     * ================= static operators =================
     */

    /**
     * Return a server resolver that resolves from a fixed list of servers using a round robin strategy
     *
     * @param servers a list of servers to resolve from
     * @return {@link ServerResolver}
     */
    public static ServerResolver from(final Server... servers) {
        return new StaticListServerResolver(servers);
    }

    /**
     * Return a server resolver that resolves from a stream of servers using a best effort round robin strategy,
     * where BufferSentinels are used to buffer and snapshot the servers to lists for round robin.
     *
     * @param serverSource an observable servers to resolve from
     * @return {@link ServerResolver}
     */
    public static ServerResolver forServerSource(final Observable<ChangeNotification<Server>> serverSource) {
        return new ObservableServerResolver(serverSource);
    }

    /**
     * Return a resolver step that contains a specified port value, and must specify a subsequent value
     * (see {@link PortResolverStep} to be able to create the final ServerResolver.
     *
     * @param port the eureka server port for communication
     * @return {@link PortResolverStep}
     */
    public static PortResolverStep withPort(int port) {
        return new DefaultPortResolverStep(port);
    }

    /**
     * Return a resolver step that contains a specified hostname, and must specify a subsequent value
     * (see {@link HostResolverStep} to be able to create the final ServerResolver.
     *
     * @param hostname a fixed hostname
     * @return {@link HostResolverStep}
     */
    public static HostResolverStep withHostname(String hostname) {
        return new FixedHostResolverStep(hostname);
    }

    /**
     * Return a resolver step that contains a dns name, and must specify a subsequent value
     * (see {@link DnsResolverStep} to be able to create the final ServerResolver. The dns is
     * resolved (if possible) to a full list of hostnames.
     *
     * @param dnsName a dnsName to resolve from
     * @return {@link DnsResolverStep}
     */
    public static DnsResolverStep withDnsName(String dnsName) {
        return new DnsResolverStep(dnsName);
    }

    /**
     * Return a file server resolver that resolves from a file using a best effort round robin strategy.
     *
     * @param file a file containing server resolver information, see {@link FileServerResolver} for specification
     * @return {@link FileServerResolver}
     */
    public static FileServerResolver fromFile(File file) {
        return new FileServerResolver(file);
    }

    /**
     * Return a server resolver step that resolves from data read from a remote eureka server. A subsequent interest
     * (see {@link EurekaRemoteResolverStep} must be specified for the final servers to resolve from the remote
     * server data.
     *
     * Note that the eureka resolver automatically comes with .loadBalance() enabled.
     *
     * @param serverToReadFrom a server resolver that resolves to the interest protocol of a remote eureka server
     * @return {@link EurekaRemoteResolverStep}
     */
    public static EurekaRemoteResolverStep fromEureka(ServerResolver serverToReadFrom) {
        return new DefaultEurekaResolverStep(serverToReadFrom);
    }


    /**
     * ================= Instance operators =================
     */

    /**
     * Return a server resolver that will fallback to another server resolver.
     *
     * @param fallback a fallback server resolver
     * @return {@link ServerResolver}
     */
    public ServerResolver withFallback(ServerResolver fallback) {
        return new FallbackServerResolver(this, fallback);
    }

    /**
     * Return a server resolver that uses a proper loadbalancer for round robin load balancing.
     *
     * @return {@link ServerResolver}
     */
    public ServerResolver loadBalance() {
        return new OcelliServerResolver(this);
    }

    /**
     * Return a server resolver that uses a proper loadbalancer for round robin load balancing. A warmUpTime can be
     * specified for this resolver past which, if no data is available to be loaded into the loadbalancer, the resolver
     * will onError.
     *
     * @param warmUpTimeout the warm up timeout
     * @param timeUnit the warm up timeout time unit
     *
     * @return {@link ServerResolver}
     */
    public ServerResolver loadBalance(int warmUpTimeout, TimeUnit timeUnit) {
        return new OcelliServerResolver(this, warmUpTimeout, timeUnit);
    }

    /**
     * ================= Methods =================
     */

    private final Observable<ChangeNotification<Server>> serverSource;

    protected ServerResolver(Server... staticServerSource) {
        this(Observable.from(staticServerSource)
                .map(new Func1<Server, ChangeNotification<Server>>() {
                    @Override
                    public ChangeNotification<Server> call(Server server) {
                        return new ChangeNotification<>(ChangeNotification.Kind.Add, server);
                    }
                })
        );
    }

    protected ServerResolver(Observable<ChangeNotification<Server>> serverSource) {
        this.serverSource = serverSource;
    }

    /**
     * Returns a single element {@link Observable} of {@link Server} instances, which
     * completes immediately after an element is provided. Properly behaving implementations should
     * never complete before issuing the value, and always complete after issuing a single value.
     * In case of a problem, a subscription should terminate with an error.
     *
     * <h1>Error handling</h1>
     * This interface does not define any specific error handling protocol, such as distinguishing between
     * recoverable and non-recoverable errors.
     *
     * @return An {@link Observable} of {@link Server} instances, which is guaranteed to onComplete after
     * the first onNext.
     */
    public abstract Observable<Server> resolve();

    public abstract void close();

    /**
     * Return an observable that emits {@link ChangeNotification}s for the full set of servers known to this
     * resolver. Optionally this stream can emit {@link com.netflix.eureka2.interests.StreamStateNotification}s
     * to help with buffering.
     *
     * @return An {@link Observable} of server events.
     */
    protected Observable<ChangeNotification<Server>> serverSource() {
        return serverSource;
    }
}
