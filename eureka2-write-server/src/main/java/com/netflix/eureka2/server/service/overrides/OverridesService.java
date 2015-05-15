package com.netflix.eureka2.server.service.overrides;

/**
 * An internal service that allows overrides to be applied to registered instanceInfos.
 *
 * For example, external overrides on an instanceInfo's status can be applied to set instances to
 * OUT_OF_SERVICE status. Information about overrides are consumed from the {@link OverridesRegistry}
 *
 *
 * @author David Liu
 */
public interface OverridesService {

}
