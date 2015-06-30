package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * An overrides source specific for setting instances' statuses to OUT_OF_SERVICE if applicable.
 * This is a very specific use case, but is necessary as it is a real use case in production.
 *
 * @author David Liu
 */
public interface InstanceStatusOverridesSource {

    /**
     * @return an observable that emits the success/failure of this operation, then onCompletes
     */
    Observable<Boolean> setOutOfService(InstanceInfo instanceInfo);

    /**
     * @return an observable that emits the success/failure of this operation, then onCompletes
     */
    Observable<Boolean> unsetOutOfService(InstanceInfo instanceInfo);
}
