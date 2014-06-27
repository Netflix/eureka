package com.netflix.eureka;

import com.netflix.appinfo.AbstractEurekaAuthInfo;
import com.netflix.appinfo.InstanceInfo;

/**
 * This class holds metadata information related to eureka server auth with peer eureka servers
 */
public class EurekaServerAuthInfo extends AbstractEurekaAuthInfo {
    public static final String DEFAULT_SERVER_NAME = "DefaultServer";

    private final String serverVersion = "1.0";
    private final String id;

    public EurekaServerAuthInfo(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return DEFAULT_SERVER_NAME;
    }

    @Override
    public String getVersion() {
        return serverVersion;
    }

    @Override
    public String getId() {
        return id;
    }
}
