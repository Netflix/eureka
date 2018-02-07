package com.netflix.appinfo;

import com.netflix.appinfo.InstanceInfo;

/**
 * A factory that can be used to create {@link com.netflix.appinfo.InstanceInfo} objects
 */
public interface EurekaInstanceInfoFactory {
    InstanceInfo get();
}
