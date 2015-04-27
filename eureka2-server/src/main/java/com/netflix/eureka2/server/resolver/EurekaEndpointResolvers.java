package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

import rx.Scheduler;

/**
 * @author Tomasz Bak
 */
public final class EurekaEndpointResolvers {

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

    private EurekaEndpointResolvers() {
    }

    public static EurekaEndpointResolver writeServerResolverFromDns(String domainName,
                                                                    int registrationPort,
                                                                    int interestPort,
                                                                    int replicationPort,
                                                                    Scheduler scheduler) {
        return DnsEurekaEndpointResolver.writeServerDnsResolver(domainName, registrationPort, interestPort, replicationPort, scheduler);
    }

    public static EurekaEndpointResolver writeServerResolverFromConfiguration(String hostnameAndPorts, boolean attemptDnsResolve, Scheduler scheduler) {
        EurekaEndpoint endpoint = EurekaEndpoint.writeServerEndpointFrom(hostnameAndPorts);
        if (attemptDnsResolve) {
            return new StaticEurekaEndpointResolver(endpoint);
        }
        return writeServerResolverFromDns(endpoint.getHostname(), endpoint.getRegistrationPort(), endpoint.getInterestPort(), endpoint.getReplicationPort(), scheduler);
    }

    public static EurekaEndpointResolver writeServerResolverFromConfiguration(List<String> hostnameAndPortsList, boolean attemptDnsResolve, Scheduler scheduler) {
        if (hostnameAndPortsList.isEmpty()) {
            throw new IllegalArgumentException("Empty host name list provided");
        }
        if (hostnameAndPortsList.size() == 1) {
            return writeServerResolverFromConfiguration(hostnameAndPortsList.get(0), attemptDnsResolve, scheduler);
        }
        List<EurekaEndpoint> eurekaEndpoints = EurekaEndpoint.writeServerEndpointsFrom(hostnameAndPortsList);
        if (!attemptDnsResolve) {
            return new StaticEurekaEndpointResolver(eurekaEndpoints);
        }
        List<EurekaEndpointResolver> resolvers = new ArrayList<>(hostnameAndPortsList.size());
        for (EurekaEndpoint endpoint : eurekaEndpoints) {
            resolvers.add(writeServerResolverFromDns(endpoint.getHostname(), endpoint.getRegistrationPort(), endpoint.getInterestPort(), endpoint.getReplicationPort(), scheduler));
        }
        return new CompositeEurekaEndpointResolver(resolvers);
    }

    public static EurekaEndpointResolver writeServerResolverFromConfiguration(ResolverType type, List<String> hostnameAndPortsList, Scheduler scheduler) {
        return writeServerResolverFromConfiguration(hostnameAndPortsList, type == ResolverType.Dns, scheduler);
    }

    public static EurekaEndpointResolver readServerResolverFromDns(String domainName, int interestPort, Scheduler scheduler) {
        return DnsEurekaEndpointResolver.readServerDnsResolver(domainName, interestPort, scheduler);
    }

    public static EurekaEndpointResolver readServerResolverFromConfiguration(String hostnameAndPorts, boolean attemptDnsResolve, Scheduler scheduler) {
        EurekaEndpoint endpoint = EurekaEndpoint.readServerEndpointFrom(hostnameAndPorts);
        if (!attemptDnsResolve) {
            return new StaticEurekaEndpointResolver(endpoint);
        }
        return readServerResolverFromDns(endpoint.getHostname(), endpoint.getInterestPort(), scheduler);
    }

    public static EurekaEndpointResolver readServerResolverFromConfiguration(List<String> hostnameAndPortsList, boolean attemptDnsResolve, Scheduler scheduler) {
        if (hostnameAndPortsList.isEmpty()) {
            throw new IllegalArgumentException("Empty host name list provided");
        }
        if (hostnameAndPortsList.size() == 1) {
            return readServerResolverFromConfiguration(hostnameAndPortsList.get(0), attemptDnsResolve, scheduler);
        }
        List<EurekaEndpoint> eurekaEndpoints = EurekaEndpoint.readServerEndpointsFrom(hostnameAndPortsList);
        if (!attemptDnsResolve) {
            return new StaticEurekaEndpointResolver(eurekaEndpoints);
        }
        List<EurekaEndpointResolver> resolvers = new ArrayList<>(hostnameAndPortsList.size());
        for (EurekaEndpoint endpoint : eurekaEndpoints) {
            resolvers.add(readServerResolverFromDns(endpoint.getHostname(), endpoint.getInterestPort(), scheduler));
        }
        return new CompositeEurekaEndpointResolver(resolvers);
    }

    public static EurekaEndpointResolver readServerResolverFromConfiguration(ResolverType type, List<String> hostnameAndPortsList, Scheduler scheduler) {
        return readServerResolverFromConfiguration(hostnameAndPortsList, type == ResolverType.Dns, scheduler);
    }
}
