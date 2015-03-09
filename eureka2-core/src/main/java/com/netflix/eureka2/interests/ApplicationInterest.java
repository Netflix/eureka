package com.netflix.eureka2.interests;

import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class ApplicationInterest extends AbstractPatternInterest<InstanceInfo> {
    protected ApplicationInterest() {
    }

    public ApplicationInterest(String application) {
        super(application);
    }

    public ApplicationInterest(String application, Operator operator) {
        super(application, operator);
    }

    @Override
    protected String getValue(InstanceInfo data) {
        return data.getApp();
    }
}