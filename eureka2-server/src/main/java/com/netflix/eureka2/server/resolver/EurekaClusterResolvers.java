package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.server.resolver.DnsEurekaClusterResolver.DnsReadServerClusterResolver;
import com.netflix.eureka2.server.resolver.DnsEurekaClusterResolver.DnsWriteServerClusterResolver;
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

    public static EurekaClusterResolver writeClusterResolverFromDns(String domainName,
                                                                    int registrationPort,
                                                                    int interestPort,
                                                                    int replicationPort,
                                                                    Scheduler scheduler) {
        return new DnsWriteServerClusterResolver(domainName, registrationPort, interestPort, replicationPort, scheduler);
    }

    public static EurekaClusterResolver writeClusterResolverFromConfiguration(String hostnameAndPorts, boolean attemptDnsResolve, Scheduler scheduler) {
        ClusterAddress address = ClusterAddress.writeClusterAddressFrom(hostnameAndPorts);
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(address);
        }
        return writeClusterResolverFromDns(address.getHostName(), address.getRegistrationPort(), address.getInterestPort(), address.getReplicationPort(), scheduler);
    }

    public static EurekaClusterResolver writeClusterResolverFromConfiguration(List<String> hostnameAndPortsList, boolean attemptDnsResolve, Scheduler scheduler) {
        if (hostnameAndPortsList.isEmpty()) {
            throw new IllegalArgumentException("Empty host name list provided");
        }
        if (hostnameAndPortsList.size() == 1) {
            return writeClusterResolverFromConfiguration(hostnameAndPortsList.get(0), attemptDnsResolve, scheduler);
        }
        List<ClusterAddress> clusterAddresses = ClusterAddress.writeClusterAddressesFrom(hostnameAndPortsList);
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(clusterAddresses);
        }
        List<EurekaClusterResolver> resolvers = new ArrayList<>(hostnameAndPortsList.size());
        for (ClusterAddress address : clusterAddresses) {
            resolvers.add(writeClusterResolverFromDns(address.getHostName(), address.getRegistrationPort(), address.getInterestPort(), address.getReplicationPort(), scheduler));
        }
        return new CompositeEurekaClusterResolver(resolvers);
    }

    public static EurekaClusterResolver writeClusterResolverFromConfiguration(ResolverType type, List<String> hostnameAndPortsList, Scheduler scheduler) {
        return writeClusterResolverFromConfiguration(hostnameAndPortsList, type == ResolverType.Dns, scheduler);
    }

    public static EurekaClusterResolver readClusterResolverFromDns(String domainName, int interestPort, Scheduler scheduler) {
        return new DnsReadServerClusterResolver(domainName, interestPort, scheduler);
    }

    public static EurekaClusterResolver readClusterResolverFromConfiguration(String hostnameAndPorts, boolean attemptDnsResolve, Scheduler scheduler) {
        ClusterAddress address = ClusterAddress.readClusterAddressFrom(hostnameAndPorts);
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(address);
        }
        return readClusterResolverFromDns(address.getHostName(), address.getInterestPort(), scheduler);
    }

    public static EurekaClusterResolver readClusterResolverFromConfiguration(List<String> hostnameAndPortsList, boolean attemptDnsResolve, Scheduler scheduler) {
        if (hostnameAndPortsList.isEmpty()) {
            throw new IllegalArgumentException("Empty host name list provided");
        }
        if (hostnameAndPortsList.size() == 1) {
            return readClusterResolverFromConfiguration(hostnameAndPortsList.get(0), attemptDnsResolve, scheduler);
        }
        List<ClusterAddress> clusterAddresses = ClusterAddress.readClusterAddressesFrom(hostnameAndPortsList);
        if (!attemptDnsResolve) {
            return new StaticEurekaClusterResolver(clusterAddresses);
        }
        List<EurekaClusterResolver> resolvers = new ArrayList<>(hostnameAndPortsList.size());
        for (ClusterAddress address : clusterAddresses) {
            resolvers.add(readClusterResolverFromDns(address.getHostName(), address.getInterestPort(), scheduler));
        }
        return new CompositeEurekaClusterResolver(resolvers);
    }

    public static EurekaClusterResolver readClusterResolverFromConfiguration(ResolverType type, List<String> hostnameAndPortsList, Scheduler scheduler) {
        return readClusterResolverFromConfiguration(hostnameAndPortsList, type == ResolverType.Dns, scheduler);
    }
}
