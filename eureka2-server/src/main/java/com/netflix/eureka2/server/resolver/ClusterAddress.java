package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent Eureka server host, and service ports.
 * <h3>String representation of host/ports</h3>
 * A single server host name with its service ports is encoded as {@literal <host_name>:<port1>[:<port2>[...]]}.
 * For Eureka write servers, there are three ports expected (registation, interest and replication). For read server,
 * a single, interest port, should be provided.
 * <p>
 * A list of servers is encoded as a comma separated list of server items. For example:
 * {@code "eureka-host1:12102:12103:12104,eureka-host2:12102:12103:12104"}
 *
 * @author Tomasz Bak
 */
public class ClusterAddress {

    public enum ServiceType {Registration, Interest, Replication}

    private final String hostname;
    private final Integer registrationPort;
    private final Integer interestPort;
    private final Integer replicationPort;

    protected ClusterAddress(String hostname, int registrationPort, int interestPort, int replicationPort) {
        this.hostname = hostname;
        this.registrationPort = registrationPort;
        this.interestPort = interestPort;
        this.replicationPort = replicationPort;
    }

    public String getHostName() {
        return hostname;
    }

    public Integer getRegistrationPort() {
        return registrationPort;
    }

    public Integer getInterestPort() {
        return registrationPort;
    }

    public Integer getReplicationPort() {
        return registrationPort;
    }

    public Integer getPortFor(ServiceType serviceType) {
        switch (serviceType) {
            case Registration:
                return getRegistrationPort();
            case Interest:
                return getInterestPort();
            case Replication:
                return getReplicationPort();
            default:
                throw new IllegalStateException("Unexpected enum value " + serviceType);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ClusterAddress{");
        sb.append("hostname='").append(hostname).append('\'');
        if (registrationPort >= 0) {
            sb.append(", registrationPort=").append(registrationPort);
        }
        if (interestPort >= 0) {
            sb.append(", interestPort=").append(interestPort);
        }
        if (replicationPort >= 0) {
            sb.append(", replicationPort=").append(replicationPort);
        }
        sb.append('}');
        return sb.toString();
    }

    public String toWriteAddressString() {
        return hostname + ':' + registrationPort + ':' + interestPort + ':' + replicationPort;
    }

    public String toReadAddressString() {
        return hostname + ':' + interestPort;
    }

    public static ClusterAddress valueOf(String domainName, int registrationPort, int interestPort, int replicationPort) {
        return new ClusterAddress(domainName, registrationPort, interestPort, replicationPort);
    }

    /**
     * The argument should be in the format: {@code <host_name>:<registration_port>:<interest_port>:<replication_port>}
     */
    public static ClusterAddress valueOf(String hostnameAndPorts) {
        try {
            String[] parts = hostnameAndPorts.split(":");
            String hostname = parts[0];
            int registrationPort = Integer.parseInt(parts[1]);
            int interestPort = Integer.parseInt(parts[2]);
            int replicationPort = Integer.parseInt(parts[3]);
            return new ClusterAddress(hostname, registrationPort, interestPort, replicationPort);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * The argument should contain a list of addresses in format described in {@link #valueOf(String)}.
     */
    public static List<ClusterAddress> valueOf(List<String> hostnameAndPortsList) {
        List<ClusterAddress> addresses = new ArrayList<>(hostnameAndPortsList.size());
        for (String aHostnameAndPortsList : hostnameAndPortsList) {
            addresses.add(valueOf(aHostnameAndPortsList));
        }
        return addresses;
    }

    public static ClusterAddress readClusterAddressFrom(String domainName, int interestPort) {
        return new ClusterAddress(domainName, interestPort, interestPort, interestPort);
    }

    /**
     * The argument should be in the format: {@code <host_name>:<interest_port>}
     */
    public static ClusterAddress readClusterAddressFrom(String hostnameAndPorts) {
        try {
            String[] parts = hostnameAndPorts.split(":");
            String hostname = parts[0];
            int interestPort = Integer.parseInt(parts[1]);
            return new ClusterAddress(hostname, -1, interestPort, -1);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * The argument should contain a list of addresses in format described in {@link #readClusterAddressFrom(String)}.
     */
    public static List<ClusterAddress> readClusterAddressesFrom(List<String> hostnameAndPortsList) {
        List<ClusterAddress> addresses = new ArrayList<>(hostnameAndPortsList.size());
        for (int i = 0; i < addresses.size(); i++) {
            addresses.add(readClusterAddressFrom(hostnameAndPortsList.get(i)));
        }
        return addresses;
    }
}
