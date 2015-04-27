package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent Eureka server host, and service ports.
 *
 * @author Tomasz Bak
 */
public class EurekaEndpoint {

    public enum ServiceType {Registration, Interest, Replication}

    private final String hostname;
    private final Integer registrationPort;
    private final Integer interestPort;
    private final Integer replicationPort;

    protected EurekaEndpoint(String hostname, int registrationPort, int interestPort, int replicationPort) {
        this.hostname = hostname;
        this.registrationPort = registrationPort;
        this.interestPort = interestPort;
        this.replicationPort = replicationPort;
    }

    public String getHostname() {
        return hostname;
    }

    public Integer getRegistrationPort() {
        return registrationPort;
    }

    public Integer getInterestPort() {
        return interestPort;
    }

    public Integer getReplicationPort() {
        return replicationPort;
    }

    public Integer getPortFor(ServiceType serviceType) {
        switch (serviceType) {
            case Registration:
                return getRegistrationPort();
            case Interest:
                return getInterestPort();
            default: // == Replication
                return getReplicationPort();
        }
    }

    public static EurekaEndpoint writeServerEndpointFrom(String domainName, int registrationPort, int interestPort, int replicationPort) {
        return new EurekaEndpoint(domainName, registrationPort, interestPort, replicationPort);
    }

    public static EurekaEndpoint writeServerEndpointFrom(String hostnameAndPorts) {
        try {
            String[] parts = hostnameAndPorts.split(":");
            String hostname = parts[0];
            int registrationPort = Integer.parseInt(parts[1]);
            int interestPort = Integer.parseInt(parts[2]);
            int replicationPort = Integer.parseInt(parts[3]);
            return new EurekaEndpoint(hostname, registrationPort, interestPort, replicationPort);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static List<EurekaEndpoint> writeServerEndpointsFrom(List<String> hostnameAndPortsList) {
        List<EurekaEndpoint> endpoints = new ArrayList<>(hostnameAndPortsList.size());
        for (String aHostnameAndPortsList : hostnameAndPortsList) {
            endpoints.add(writeServerEndpointFrom(aHostnameAndPortsList));
        }
        return endpoints;
    }

    public static EurekaEndpoint readServerEndpointFrom(String domainName, int interestPort) {
        return new EurekaEndpoint(domainName, -1, interestPort, -1);
    }

    public static EurekaEndpoint readServerEndpointFrom(String hostnameAndPorts) {
        try {
            String[] parts = hostnameAndPorts.split(":");
            String hostname = parts[0];
            int interestPort = Integer.parseInt(parts[1]);
            return new EurekaEndpoint(hostname, -1, interestPort, -1);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static List<EurekaEndpoint> readServerEndpointsFrom(List<String> hostnameAndPortsList) {
        List<EurekaEndpoint> endpoints = new ArrayList<>(hostnameAndPortsList.size());
        for (int i = 0; i < endpoints.size(); i++) {
            endpoints.add(readServerEndpointFrom(hostnameAndPortsList.get(i)));
        }
        return endpoints;
    }
}
