/*
 * V1AwareInstanceInfoConverter.java
 *  
 * $Header: //depot/webapplications/eureka/main/src/com/netflix/discovery/V1AwareInstanceInfoConverter.java#2 $ 
 * $DateTime: 2012/07/23 17:59:17 $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

import org.slf4j.LoggerFactory;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.converters.Converters.InstanceInfoConverter;
import org.slf4j.Logger;

public class V1AwareInstanceInfoConverter extends InstanceInfoConverter {
    private static final Logger logger = LoggerFactory.getLogger(V1AwareInstanceInfoConverter.class); 
    @Override
    public String getStatus(InstanceInfo info) {
        Version version = CurrentRequestVersion.get();
        logger.debug("The version that is requested for marshalling is {}", (version != null ? version.name() : "NULL"));
        if(version == null || version == Version.V1){
            InstanceStatus status = info.getStatus();
              switch (status) {
              //known status enums in v1
              case DOWN:
              case STARTING:
              case UP:
                  break;
              default:
                  //otherwise return DOWN
                  status = InstanceStatus.DOWN;
                  break;
              }
              return status.name();
        }else {
            return super.getStatus(info);
        }
    }
    
}
