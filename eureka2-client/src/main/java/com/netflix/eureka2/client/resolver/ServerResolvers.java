package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.notification.ChangeNotification;
import rx.Observable;

import java.io.File;

/**
 * Class for creating various flavours of {@link ServerResolver}s.
 *
 * @author David Liu
 */
public class ServerResolvers {

    /**
     * Return a server resolver that resolves from a fixed list of servers using a round robin strategy
     *
     * @param servers a list of servers to resolve from
     * @return {@link ServerResolver}
     */
    public static ServerResolver from(final Server... servers) {
        return new RoundRobinServerResolver(servers);
    }

    /**
     * Return a server resolver that resolves from a stream of servers using a best effort round robin strategy,
     * where BufferSentinels are used to buffer and snapshot the servers to lists for round robin.
     *
     * @param serverSource an observable servers to resolve from
     * @return {@link ServerResolver}
     */
    public static ServerResolver fromServerSource(final Observable<ChangeNotification<Server>> serverSource) {
        return new RoundRobinServerResolver(serverSource);
    }

    /**
     * Return a resolver step that contains a specified port value, and must specify a subsequent value
     * (see {@link PortResolverStep} to be able to create the final ServerResolver.
     *
     * @param port the eureka server port for communication
     * @return {@link PortResolverStep}
     */
    public static PortResolverStep fromPort(int port) {
        return new DefaultPortResolverStep(port);
    }

    /**
     * Return a resolver step that contains a specified hostname, and must specify a subsequent value
     * (see {@link HostResolverStep} to be able to create the final ServerResolver.
     *
     * @param hostname a fixed hostname
     * @return {@link HostResolverStep}
     */
    public static HostResolverStep fromHostname(String hostname) {
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
    public static DnsResolverStep fromDnsName(String dnsName) {
        return new DnsResolverStep(dnsName);
    }

    /**
     * Return a file server resolver that resolves from a file using a best effort round robin strategy.
     *
     * @param file a file containing server resolver information, see {@link FileServerResolver} for specification
     * @return {@link FileServerResolver}
     */
    public static ServerResolver fromFile(File file) {
        return new FileServerResolver(file);
    }

    /**
     * Return a server resolver step that resolves from data read from a remote eureka server. A subsequent interest
     * (see {@link EurekaRemoteResolverStep} must be specified for the final servers to resolve from the remote
     * server data.
     *
     * @param serverToReadFrom a server resolver that resolves to the interest protocol of a remote eureka server
     * @return {@link EurekaRemoteResolverStep}
     */
    public static EurekaRemoteResolverStep fromEureka(ServerResolver serverToReadFrom) {
        return new DefaultEurekaResolverStep(serverToReadFrom);
    }

    /**
     * Return a server resolver that resolves from the primary resolver, and if that onError will backback to
     * the specified fallback resolver.
     *
     * @param primary the primary resolver to resolve from
     * @param fallback the fallback resolver if resolve() on the primary Resolver onError.
     * @return {@link ServerResolver}
     */
    public static ServerResolver fallbackResolver(ServerResolver primary, ServerResolver fallback) {
        return new FallbackServerResolver(primary, fallback);
    }
}
