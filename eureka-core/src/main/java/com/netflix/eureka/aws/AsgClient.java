package com.netflix.eureka.aws;

import com.netflix.appinfo.InstanceInfo;

public interface AsgClient {
    boolean isASGEnabled(InstanceInfo instanceInfo);

    void setStatus(String asgName, boolean enabled);
}
