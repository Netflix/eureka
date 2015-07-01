package com.netflix.eureka2.ext.aws;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.service.overrides.InstanceStatusOverridesService;

/**
 * @author David Liu
 */
@Singleton
public class AsgStatusOverridesService extends InstanceStatusOverridesService {

    @Inject
    public AsgStatusOverridesService(AsgStatusOverridesView asgStatusRegistry) {
        super(asgStatusRegistry);
    }
}
