package com.netflix.eureka2.client.interest;

import com.netflix.eureka2.client.functions.ChangeNotificationFunctions;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

/**
 * An eureka client that support reading interested data from remote eureka servers.
 *
 * @author David Liu
 */
public interface EurekaInterestClient {

    /**
     * Return an observable that when subscribed to, will emit an infinite stream of {@link ChangeNotification} of
     * the Interested items. This stream will only onComplete on client shutdown, and can be retried when onError.
     *
     * @param interest an Interest specifying the matching criteria for InstanceInfos. See {@link Interests}
     *                 for methods to construct interests.
     * @return an observable of {@link ChangeNotification}s of the interested InstanceInfos. The notifications
     *         returned may contain interleaved data notifications and streamState notifications. Standard
     *         transformers for transforming the returned stream is available in {@link ChangeNotificationFunctions}.
     */
    Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest);

    /**
     * shutdown and clean up all resources for this client
     */
    void shutdown();

}
