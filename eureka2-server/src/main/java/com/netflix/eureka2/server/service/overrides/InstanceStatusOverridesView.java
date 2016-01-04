package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

/**
 * An overrides view specific for querying whether an instance's status should be overridden with
 * OUT_OF_SERVICE. This is a very specific use case, but is necessary as it is a real use case in production.
 *
 * @author David Liu
 */
public interface InstanceStatusOverridesView {

    /**
     * @return an observable that emits a stream of booleans denoting whether OOS should be applied to the specified
     * instance.
     */
    Observable<Boolean> shouldApplyOutOfService(InstanceInfo instanceInfo);
}
