package com.netflix.eureka;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class ChangeNotifications {

    public static final ChangeNotification<InstanceInfo> ZuulAddNotification =
            new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, SampleInstanceInfo.ZuulServer.build());

    public static final ChangeNotification<InstanceInfo> ZuulDeleteNotification =
            new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete, SampleInstanceInfo.ZuulServer.build());

    public static final ChangeNotification<InstanceInfo> DiscoveryAddNotification =
            new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, SampleInstanceInfo.DiscoveryServer.build());

    public static final ChangeNotification<InstanceInfo> DiscoveryDeleteNotification =
            new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Delete, SampleInstanceInfo.DiscoveryServer.build());
}
