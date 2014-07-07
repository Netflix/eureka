package com.netflix.appinfo;

import javax.annotation.Nullable;

public abstract class AbstractEurekaIdentity {
    public static final String PREFIX = "DiscoveryIdentity-";

    public static final String AUTH_NAME_HEADER_KEY = PREFIX + "Name";
    public static final String AUTH_VERSION_HEADER_KEY = PREFIX + "Version";
    public static final String AUTH_ID_HEADER_KEY = PREFIX + "Id";

    public abstract String getName();

    public abstract String getVersion();

    @Nullable
    public abstract String getId();
}
