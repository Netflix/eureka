package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.interests.ChangeNotification;
import rx.Observable;

/**
 * A registry that holds override data that can be applied to instanceInfos.
 *
 * @author David Liu
 */
public interface OverridesRegistry {

    /**
     * Get all overrides for a given id
     *
     * @param id
     * @return
     */
    Overrides get(String id);


    /**
     * Set all overrides for the given id. If there are any previous overrides, they are discarded.
     *
     * Implementations will need to deal with concurrency between set and remove, if any.
     *
     * @param overrides
     * @return an Observable<Void> that will onComplete or onError based on the operation success/failure
     */
    Observable<Void> set(Overrides overrides);


    /**
     * Remove all current overrides for the given id.
     *
     * Implementations will need to deal with concurrency between set and remove, if any.
     *
     * @param id
     * @return an Observable<Void> that will onComplete or onError based on the operation success/failure
     */
    Observable<Void> remove(String id);

    /**
     * On subscribe emits latest override for the given instance id, or if no override present
     * emits delete override notification.
     *
     * @param id the id of the override stream to return
     * @return a stream of updates on additions and removals for overrides
     */
    Observable<ChangeNotification<Overrides>> forUpdates(String id);

    /**
     * Shutdown the overrides registry
     */
    void shutdown();
}
