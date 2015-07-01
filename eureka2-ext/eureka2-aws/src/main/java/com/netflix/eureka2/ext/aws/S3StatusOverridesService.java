package com.netflix.eureka2.ext.aws;

import com.netflix.eureka2.server.service.overrides.InstanceStatusOverridesService;

import javax.inject.Inject;

/**
 * @author David Liu
 */
public class S3StatusOverridesService extends InstanceStatusOverridesService {

    @Inject
    public S3StatusOverridesService(S3StatusOverridesRegistry asgStatusRegistry) {
        super(asgStatusRegistry);
    }
}
