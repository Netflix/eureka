package com.netflix.eureka;

import com.netflix.appinfo.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public interface PeerAwareInstanceRegistry extends InstanceRegistry {
    void register(InstanceInfo info, boolean isReplication);
}
