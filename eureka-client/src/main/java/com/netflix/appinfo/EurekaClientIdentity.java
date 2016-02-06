package com.netflix.appinfo;

/**
 * This class holds metadata information related to eureka client auth with the eureka server
 */
public class EurekaClientIdentity extends AbstractEurekaIdentity {
    public static final String DEFAULT_CLIENT_NAME = "DefaultClient";

    private final String clientVersion = "1.4";
    private final String id;

    public EurekaClientIdentity(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return DEFAULT_CLIENT_NAME;
    }

    @Override
    public String getVersion() {
        return clientVersion;
    }

    @Override
    public String getId() {
        return id;
    }
}
