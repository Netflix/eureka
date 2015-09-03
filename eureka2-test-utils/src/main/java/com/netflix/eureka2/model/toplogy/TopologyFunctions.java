package com.netflix.eureka2.model.toplogy;

import java.util.Iterator;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.notification.ModifyNotification;
import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public final class TopologyFunctions {

    public static final String SERVER_ID_KEY = "testServerId";
    public static final String TIME_STAMP_KEY = "timestamp";

    private TopologyFunctions() {
    }

    public static InstanceInfo addServerId(InstanceInfo instanceInfo, String serverId) {
        return new InstanceInfo.Builder()
                .withInstanceInfo(instanceInfo)
                .withMetaData(SERVER_ID_KEY, serverId)
                .build();
    }

    public static String getSeverId(ChangeNotification<InstanceInfo> notification) {
        return notification.getData().getMetaData().get(SERVER_ID_KEY);
    }

    public static InstanceInfo addTimeStamp(InstanceInfo instanceInfo) {
        return addTimeStamp(instanceInfo, System.currentTimeMillis());
    }

    public static InstanceInfo addTimeStamp(InstanceInfo instanceInfo, long timeStamp) {
        return new InstanceInfo.Builder()
                .withInstanceInfo(instanceInfo)
                .withMetaData(TIME_STAMP_KEY, Long.toString(timeStamp))
                .build();
    }

    public static ChangeNotification<InstanceInfo> addTimeStamp(ChangeNotification<InstanceInfo> notification) {
        return addTimeStamp(notification, System.currentTimeMillis());
    }

    public static ChangeNotification<InstanceInfo> addTimeStamp(ChangeNotification<InstanceInfo> notification, long timeStamp) {
        InstanceInfo timeStamped = addTimeStamp(notification.getData(), timeStamp);
        switch (notification.getKind()) {
            case Add:
            case Delete:
                return new ChangeNotification<>(notification.getKind(), timeStamped);
            case Modify:
                return new ModifyNotification<>(timeStamped, ((ModifyNotification) notification).getDelta());
            default:
                throw new IllegalArgumentException("Unexpected enum value " + notification.getKind());
        }
    }

    public static Long getTimestamp(ChangeNotification<InstanceInfo> notification) {
        String valueString = notification.getData().getMetaData().get(TIME_STAMP_KEY);
        return valueString == null ? null : Long.parseLong(valueString);
    }

    public static Iterator<ChangeNotification<InstanceInfo>> changeNotificationIteratorOf(final Iterator<InstanceInfo> instanceInfoIterator) {
        return new Iterator<ChangeNotification<InstanceInfo>>() {
            @Override
            public boolean hasNext() {
                return instanceInfoIterator.hasNext();
            }

            @Override
            public ChangeNotification<InstanceInfo> next() {
                return new ChangeNotification<>(Kind.Add, instanceInfoIterator.next());
            }

            @Override
            public void remove() {
            }
        };
    }
}
