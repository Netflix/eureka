package com.netflix.appinfo;

public interface RefreshableInstanceConfig {

    /**
     * resolve the default address
     *
     * @param refresh
     * @return
     */
    String resolveDefaultAddress(boolean refresh);
}
