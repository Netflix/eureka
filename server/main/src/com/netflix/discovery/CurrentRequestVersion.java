/*
 * CurrentRequestVersion.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/CurrentRequestVersion.java#2 $ 
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;


/**
 * A thread-scoped value that holds the "current {@link Version}" for the
 * request.
 * 
 * This uses the {@link ThreadVariable} mechanism to hold the value,
 * so the "current {@link Version}" will be subject to its policies.
 *
 * <p>This is not intended as a general mechanism for passing data.
 * Rather it is here to support those cases where someplace deep in
 * a library we need to know about the context of the request that
 * initially triggered the current request.
 */
public final class CurrentRequestVersion {

    private static final ThreadLocal<Version> CURRENT_REQ_VERSION =
        new ThreadLocal<Version>();

    private CurrentRequestVersion() { }

    /**
     * Gets the current {@link Version}
     * Will return null if no current version has been set.
     */
    public static Version get() {
        return CURRENT_REQ_VERSION.get();
    }

    /**
     * Sets the current {@link Version}
     */
    public static void set(Version version) {
        CURRENT_REQ_VERSION.set(version);
    }

}