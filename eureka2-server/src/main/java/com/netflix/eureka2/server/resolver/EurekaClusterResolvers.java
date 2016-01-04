package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.server.resolver.DnsEurekaClusterResolver.DnsEurekaServerClusterResolver;
import rx.Scheduler;

/**
 * @author Tomasz Bak
 */
public final class EurekaClusterResolvers {

    public enum ResolverType {
        Fixed,
        Dns;

        public static ResolverType valueOfIgnoreCase(String name) {
            for (ResolverType type : values()) {
                if (name.equalsIgnoreCase(type.name())) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Invalid ResolverType name " + name);
        }
    }

    private EurekaClusterResolvers() {
    }

    public static EurekaClusterResolver writeClusterResolverFromDns(String domainName, int serverPort, Scheduler scheduler) {
        return new DnsEurekaServerClusterResolver(domainName, serverPort, scheduler);
    }

    public static EurekaClusterResolver writeClusterResolverFromConfiguration(ClusterAddress address, boolean attemptDnsResolve, Scheduler scheduler) {
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(address);
        }
        return writeClusterResolverFromDns(address.getHostName(), address.getPort(), scheduler);
    }

    public static EurekaClusterResolver writeClusterResolverFromConfiguration(List<ClusterAddress> clusterAddresses, boolean attemptDnsResolve, Scheduler scheduler) {
        if (clusterAddresses.isEmpty()) {
            throw new IllegalArgumentException("Empty host name list provided");
        }
        if (clusterAddresses.size() == 1) {
            return writeClusterResolverFromConfiguration(clusterAddresses.get(0), attemptDnsResolve, scheduler);
        }
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(clusterAddresses);
        }
        List<EurekaClusterResolver> resolvers = new ArrayList<>(clusterAddresses.size());
        for (ClusterAddress address : clusterAddresses) {
            resolvers.add(writeClusterResolverFromDns(address.getHostName(), address.getPort(), scheduler));
        }
        return new CompositeEurekaClusterResolver(resolvers);
    }

    public static EurekaClusterResolver writeClusterResolverFromConfiguration(ResolverType type, List<ClusterAddress> clusterAddresses, Scheduler scheduler) {
        return writeClusterResolverFromConfiguration(clusterAddresses, type == ResolverType.Dns, scheduler);
    }

    public static EurekaClusterResolver readClusterResolverFromDns(String domainName, int serverPort, Scheduler scheduler) {
        return new DnsEurekaServerClusterResolver(domainName, serverPort, scheduler);
    }

    public static EurekaClusterResolver readClusterResolverFromConfiguration(ClusterAddress address, boolean attemptDnsResolve, Scheduler scheduler) {
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(address);
        }
        return readClusterResolverFromDns(address.getHostName(), address.getPort(), scheduler);
    }

    public static EurekaClusterResolver readClusterResolverFromConfiguration(List<ClusterAddress> clusterAddresses, boolean attemptDnsResolve, Scheduler scheduler) {
        if (clusterAddresses.isEmpty()) {
            throw new IllegalArgumentException("Empty host name list provided");
        }
        if (clusterAddresses.size() == 1) {
            return readClusterResolverFromConfiguration(clusterAddresses.get(0), attemptDnsResolve, scheduler);
        }
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(clusterAddresses);
        }
        List<EurekaClusterResolver> resolvers = new ArrayList<>(clusterAddresses.size());
        for (ClusterAddress address : clusterAddresses) {
            resolvers.add(readClusterResolverFromDns(address.getHostName(), address.getPort(), scheduler));
        }
        return new CompositeEurekaClusterResolver(resolvers);
    }

    public static EurekaClusterResolver readClusterResolverFromConfiguration(ResolverType type, List<ClusterAddress> hostnameAndPortsList, Scheduler scheduler) {
        return readClusterResolverFromConfiguration(hostnameAndPortsList, type == ResolverType.Dns, scheduler);
    }
}
