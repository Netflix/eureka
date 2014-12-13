package com.netflix.eureka2.testkit.data.builder;


import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.registry.NetworkAddress;
import com.netflix.eureka2.registry.NetworkAddress.NetworkAddressBuilder;
import com.netflix.eureka2.registry.NetworkAddress.ProtocolType;

import static com.netflix.eureka2.registry.NetworkAddress.NetworkAddressBuilder.aNetworkAddress;

/**
 * @author Tomasz Bak
 */
public enum SampleNetworkAddress {
    PublicIPv4() {
        @Override
        public NetworkAddressBuilder builder() {
            return aNetworkAddress()
                    .withHostName("test.public.host")
                    .withIpAddress("11.11.0.1")
                    .withProtocolType(ProtocolType.IPv4);
        }
    },
    PrivateIpV4() {
        @Override
        public NetworkAddressBuilder builder() {
            return aNetworkAddress()
                    .withHostName("test.private.host")
                    .withIpAddress("192.168.0.1")
                    .withProtocolType(ProtocolType.IPv4);
        }
    };

    public abstract NetworkAddressBuilder builder();

    public NetworkAddress build() {
        return builder().build();
    }

    /**
     * Generates a sequence of {@link NetworkAddress} objects.
     * <h1>Example</h1>
     * Given networkAddressPrefix=10.10.10, and hostDomain="eureka.net", the generated sequence will be:
     *   {hostName=10_10_10_1.eureka.net, ip=10.10.10.1},
     *   {hostName=10_10_10_2.eureka.net, ip=10.10.10.2},
     * etc.
     *
     * @param networkAddressPrefix network address part, that will be complemented to generate a host address
     * @param hostDomain root domain name, that will be complemented with a name generated from the IP address
     */
    public static Iterator<NetworkAddress> collectionOfIPv4(final String networkAddressPrefix,
                                                            final String hostDomain,
                                                            final String label) {
        int parts = 3;
        int mask = 1;
        for (int pos = 0; pos < networkAddressPrefix.length(); pos++) {
            if (networkAddressPrefix.charAt(pos) == '.') {
                parts--;
                mask *= 256;
            }
        }
        if (parts <= 0 || networkAddressPrefix.isEmpty()) {
            throw new IllegalArgumentException("Invalid network address prefix");
        }
        final AtomicLong counter = new AtomicLong();
        final int finalMask = mask;
        final int finalParts = parts;
        return new Iterator<NetworkAddress>() {
            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public NetworkAddress next() {
                int hostPart = (int) (counter.incrementAndGet() % finalMask);
                String nextIp = nextIpAddress(networkAddressPrefix, hostPart, finalParts);
                String nextHostName = hostNameFrom(hostDomain, nextIp);
                return aNetworkAddress()
                        .withHostName(nextHostName)
                        .withIpAddress(nextIp)
                        .withLabel(label)
                        .withProtocolType(ProtocolType.IPv4)
                        .build();
            }

            @Override
            public void remove() {
                throw new IllegalStateException("Operation not supported");
            }
        };
    }

    protected static String nextIpAddress(String networkAddressPrefix, int hostPart, int finalParts) {
        StringBuilder ipBuilder = new StringBuilder(networkAddressPrefix);
        int div = 1 << 8 * (finalParts - 1);
        for (int i = 0; i < finalParts; i++) {
            ipBuilder.append('.').append(hostPart / div);
            hostPart %= div;
            div >>= 8;
        }
        return ipBuilder.toString();
    }

    protected static String hostNameFrom(String hostDomain, String ipAddress) {
        StringBuilder hostBuilder = new StringBuilder("ip");
        for (char c : ipAddress.toCharArray()) {
            hostBuilder.append(c == '.' ? '_' : c);
        }
        return hostBuilder.append('.').append(hostDomain).toString();
    }
}
