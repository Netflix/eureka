package com.netflix.eureka2.server.config;

import javax.annotation.Nullable;

import com.netflix.archaius.annotations.DefaultValue;
import com.netflix.eureka2.server.resolver.ClusterAddress;
import com.netflix.eureka2.server.resolver.EurekaClusterResolvers.ResolverType;

/**
 * @author Tomasz Bak
 */
public interface BootstrapConfig {

    boolean DEFAULT_BOOTSTRAP_ENABLED = false;

    long DEFAULT_BOOTSTRAP_TIMEOUT_MS = 30000;

    @DefaultValue("" + DEFAULT_BOOTSTRAP_ENABLED)
    boolean isBootstrapEnabled();

    @DefaultValue("Fixed")
    ResolverType getBootstrapResolverType();

    @Nullable
    ClusterAddress[] getBootstrapClusterAddresses();

    @DefaultValue("" + DEFAULT_BOOTSTRAP_TIMEOUT_MS)
    long getBootstrapTimeoutMs();

}
