package com.netflix.eureka.interests;

import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class ApplicationInterest extends Interest<InstanceInfo> {

    private final String applicationName;

    public ApplicationInterest(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationName() {
        return applicationName;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return applicationName.equals(data.getApp());
    }
}
