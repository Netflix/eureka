/*
 * DataCenterInfo.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.appinfo;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Application info specific to the datacenter
 * 
 * @author gkim
 */
@XStreamAlias("datacenter")
public interface DataCenterInfo {
   enum Name {Netflix, Amazon};
    
   Name getName();
}
