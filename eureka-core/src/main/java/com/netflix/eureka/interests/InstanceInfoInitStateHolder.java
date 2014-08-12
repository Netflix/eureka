package com.netflix.eureka.interests;

import com.netflix.eureka.datastore.NotificationsSubject;
import com.netflix.eureka.registry.InstanceInfo;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * An {@link Index.InitStateHolder} implementation for {@link InstanceInfo}
 *
 * @author Nitesh Kant
 */
public class InstanceInfoInitStateHolder extends Index.InitStateHolder<InstanceInfo> {

    // TODO: Implement compaction
    private final ConcurrentLinkedQueue<ChangeNotification<InstanceInfo>> notifications;

    public InstanceInfoInitStateHolder(Iterator<ChangeNotification<InstanceInfo>> initialRegistry) {
        super(NotificationsSubject.<InstanceInfo>create());
        notifications = new ConcurrentLinkedQueue<ChangeNotification<InstanceInfo>>();
        while (initialRegistry.hasNext()) {
            notifications.add(initialRegistry.next());
        }
    }

    @Override
    protected void addNotification(ChangeNotification<InstanceInfo> notification) {
        notifications.add(notification);
    }

    @Override
    protected void clearAllNotifications() {
        notifications.clear();
    }

    @Override
    protected Iterator<ChangeNotification<InstanceInfo>> _newIterator() {
        return notifications.iterator();
    }
}
