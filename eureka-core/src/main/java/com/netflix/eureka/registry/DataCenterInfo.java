package com.netflix.eureka.registry;

/**
 * {@link DataCenterInfo} encapsulates information about the data center where a given
 * server is running, plus server specific information, like IP addresses, host names, etc.
 *
 * Because for certain dataceneters there are multiple network interfaces per server,
 * it is impportant to choose optimal interfaces for a pair of servers (private for collocated servers,
 * public if in different regions, etc). To support this process in a transparent way
 * eureka-client API provides peer address resolver abstractions.
 *
 * @author David Liu
 */
public abstract class DataCenterInfo {

    public abstract String getName();

    public abstract java.util.List<NetworkAddress> getAddresses();

    public NetworkAddress getFirstPublicAddress() {
        for (NetworkAddress n : getAddresses()) {
            if (n.isPublic()) {
                return n;
            }
        }
        return null;
    }

    public abstract static class DataCenterInfoBuilder<I extends DataCenterInfo> {
        public abstract I build();
    }
}
