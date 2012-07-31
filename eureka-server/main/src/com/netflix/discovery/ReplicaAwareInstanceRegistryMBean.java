/*
 * ReplicaAwareInstanceRegistryMBean.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/ReplicaAwareInstanceRegistryMBean.java#1 $ 
 * $DateTime: 2012/06/18 13:12:02 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

public interface ReplicaAwareInstanceRegistryMBean {
    void setNumOfRenewsPerMinThreshold(int newThreshold);
    int getNumOfRenewsPerMinThreshold();
}
