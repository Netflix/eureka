package com.netflix.eureka2.testkit.netrouter;

import com.netflix.eureka2.testkit.netrouter.internal.NetworkRouterImpl;

/**
 * A factory class for {@link NetworkRouter} instances.
 *
 * @author Tomasz Bak
 */
public final class NetworkRouters {

    private NetworkRouters() {
    }

    public static NetworkRouter aRouter() {
        return new NetworkRouterImpl();
    }
}
