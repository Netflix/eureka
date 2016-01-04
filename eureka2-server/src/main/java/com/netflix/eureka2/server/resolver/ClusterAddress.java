package com.netflix.eureka2.server.resolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Represent Eureka server host, and service port.
 * <h3>String representation of host/ports</h3>
 * A single server host name with its service port is encoded as {@literal <host_name>:<port>}.
 * <p>
 * A list of servers is encoded as a comma separated list of server items. For example:
 * {@code "eureka-host1:12102,eureka-host2:12102"}
 *
 * @author Tomasz Bak
 */
public class ClusterAddress {

    private final String hostname;
    private final Integer port;

    protected ClusterAddress(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public String getHostName() {
        return hostname;
    }

    public Integer getPort() {
        return port;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ClusterAddress{");
        sb.append("hostname='").append(hostname).append('\'');
        if (port >= 0) {
            sb.append(", port=").append(port);
        }
        sb.append('}');
        return sb.toString();
    }

    public static ClusterAddress valueOf(String domainName, int registrationPort) {
        return new ClusterAddress(domainName, registrationPort);
    }

    /**
     * The argument should be in the format: {@code <host_name>:<registration_port>:<interest_port>:<replication_port>}
     */
    public static ClusterAddress valueOf(String hostnameAndPorts) {
        try {
            String[] parts = hostnameAndPorts.split(":");
            String hostname = parts[0];
            int registrationPort = Integer.parseInt(parts[1]);
            return new ClusterAddress(hostname, registrationPort);
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

    public static ClusterAddress readClusterAddressFrom(String domainName, int serverPort) {
        return new ClusterAddress(domainName, serverPort);
    }
}
