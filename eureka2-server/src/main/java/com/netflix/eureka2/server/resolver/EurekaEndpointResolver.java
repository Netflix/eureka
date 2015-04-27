package com.netflix.eureka2.server.resolver;

import com.netflix.eureka2.interests.ChangeNotification;
import rx.Observable;

/**
 * Server side equivalent abstraction of a resolver providing available Eureka servers.
 * Unlike client side version, that returns always a single element on subscribe, on the server side
 * we return an observable change notification stream of cluster topology changes. This is required as in
 * most cases we need to know all servers, not a particular, randomly chosen one.
 * <h3>Batching</h3>
 * Buffer sentinels are used to delineate the buffers.
 *
 * @author Tomasz Bak
 */
public interface EurekaEndpointResolver {
    Observable<ChangeNotification<EurekaEndpoint>> eurekaEndpoints();
}
