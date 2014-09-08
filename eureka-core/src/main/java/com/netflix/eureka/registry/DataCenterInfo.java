package com.netflix.eureka.registry;

import java.util.ArrayList;
import java.util.List;

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

    public abstract List<NetworkAddress> getAddresses();

    public List<NetworkAddress> getPublicAddresses() {
        List<NetworkAddress> publicAddresses = new ArrayList<>();
        for (NetworkAddress n : getAddresses()) {
            if (n.isPublic()) {
                publicAddresses.add(n);
            }
        }
        return publicAddresses;
    }

    public List<NetworkAddress> getPrivateAddresses() {
        List<NetworkAddress> privateAddresses = new ArrayList<>();
        for (NetworkAddress n : getAddresses()) {
            if (!n.isPublic()) {
                privateAddresses.add(n);
            }
        }
        return privateAddresses;
    }

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
