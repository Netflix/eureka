package com.netflix.eureka2.channel;

import com.netflix.eureka2.registry.Sourced;

/**
 * @author Tomasz Bak
 */
public interface BridgeChannel extends ServiceChannel, Sourced {

    enum STATE {Opened, Closed}

    void connect();
}
