package com.netflix.eureka;

import com.netflix.appinfo.AbstractEurekaIdentity;

/**
 * This class holds metadata information related to eureka server auth with peer eureka servers
 */
public class EurekaServerIdentity extends AbstractEurekaIdentity {
    public static final String DEFAULT_SERVER_NAME = "DefaultServer";

    private final String serverVersion = "1.0";
    private final String id;

    public EurekaServerIdentity(String id) {
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
