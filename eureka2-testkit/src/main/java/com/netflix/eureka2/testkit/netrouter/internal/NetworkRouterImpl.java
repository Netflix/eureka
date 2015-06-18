package com.netflix.eureka2.testkit.netrouter.internal;

import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.testkit.netrouter.NetworkLink;
import com.netflix.eureka2.testkit.netrouter.NetworkRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
@Singleton
public class NetworkRouterImpl implements NetworkRouter {

    private static final Logger logger = LoggerFactory.getLogger(NetworkRouterImpl.class);

    private final Map<Integer, RouterPort> routerSocketsByPort1 = new HashMap<>();
    private final Map<Integer, RouterPort> routerSocketsByPort2 = new HashMap<>();

    @Override
    public int bridgeTo(int targetPort) {
        RouterPort routerPort = new RouterPort(targetPort);
        int localPort = routerPort.getLocalPort();

        routerSocketsByPort1.put(targetPort, routerPort);
        routerSocketsByPort1.put(localPort, routerPort);

        logger.info("Port {} shadowed by {}", targetPort, localPort);

        return localPort;
    }

    @Override
    public void removeBridgeTo(int targetPort) {
        RouterPort routerPort = routerSocketsByPort1.remove(targetPort);
        if (routerPort == null) {
            routerPort = routerSocketsByPort2.remove(targetPort);
        }
        if (routerPort != null) {
            routerPort.shutdown();
        }
    }

    @Override
    public NetworkLink getLinkTo(int port) {
        RouterPort routerPort = routerSocketsByPort1.get(port);
        if (routerPort == null) {
            routerPort = routerSocketsByPort2.get(port);
        }
        return routerPort == null ? null : routerPort.getLink();
    }

    @PreDestroy
    @Override
    public void shutdown() {
        for (RouterPort routerPort : routerSocketsByPort1.values()) {
            routerPort.shutdown();
        }
        routerSocketsByPort1.clear();
        routerSocketsByPort2.clear();
    }
}
