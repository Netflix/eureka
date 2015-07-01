package com.netflix.eureka2.server.config;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;

/**
 * @author Tomasz Bak
 */
public interface EurekaClusterDiscoveryConfig {

    @DefaultValue("Fixed")
    ResolverType getClusterResolverType();

    @DefaultValue("localhost:12102:12103:12104")
    ClusterAddress[] getClusterAddresses();

    @DefaultValue("eureka2-read")
    String getReadClusterVipAddress();
}
