package com.netflix.eureka2.interests;

import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.StreamStateNotification.BufferingState;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.eureka2.utils.ExtCollections.concat;
import static com.netflix.eureka2.utils.ExtCollections.singletonIterator;

/**
 * An {@link Index.InitStateHolder} implementation for {@link InstanceInfo}.
 * As the cached state is backed by {@link ConcurrentHashMap}, the order of notifications
 * is not preserved, which is fine as there is exactly one item per {@link InstanceInfo} object.
 *
 * @author Nitesh Kant
 */
public class InstanceInfoInitStateHolder extends Index.InitStateHolder<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(InstanceInfoInitStateHolder.class);

    private final ConcurrentHashMap<String, ChangeNotification<InstanceInfo>> notificationMap;
    private final ChangeNotification<InstanceInfo> bufferNotificaton;
    private final ChangeNotification<InstanceInfo> finishBufferingNotificaton;

    public InstanceInfoInitStateHolder(Iterator<ChangeNotification<InstanceInfo>> initialRegistry, Interest<InstanceInfo> interest) {
        super(NotificationsSubject.<InstanceInfo>create());
        this.bufferNotificaton = new StreamStateNotification<>(BufferingState.Buffer, interest);
        this.finishBufferingNotificaton = new StreamStateNotification<>(BufferingState.FinishBuffering, interest);
        notificationMap = new ConcurrentHashMap<>();

        while (initialRegistry.hasNext()) {
            ChangeNotification<InstanceInfo> next = initialRegistry.next();
            notificationMap.put(next.getData().getId(), next); // Always Kind.Add
        }
    }

    @Override
    protected void addNotification(ChangeNotification<InstanceInfo> notification) {
        String id = notification.getData().getId();
        ChangeNotification<InstanceInfo> current = notificationMap.get(id);

        ChangeNotification<InstanceInfo> updated = processNext(current, notification);
        if (updated != null) {
            notificationMap.put(id, updated);
        } else if (current != null) {
            notificationMap.remove(id);
        }
    }

    @Override
    protected void clearAllNotifications() {
        notificationMap.clear();
    }

    @Override
    protected Iterator<ChangeNotification<InstanceInfo>> _newIterator() {
        if (notificationMap.isEmpty()) {
            return Collections.emptyIterator();
        }
        return concat(
                singletonIterator(bufferNotificaton),
                notificationMap.values().iterator(),
                singletonIterator(finishBufferingNotificaton)
        );
    }

    private static ChangeNotification<InstanceInfo> processNext(ChangeNotification<InstanceInfo> current,
                                                                ChangeNotification<InstanceInfo> update) {
        switch (update.getKind()) {
            case Add:
                // Add flushes previous state
                return update;
            case Modify:
                // Re-write as add
                if (current == null) {
                    logger.info("Invalid change notification sequence - " +
                                    "'Modify' ChangeNotification without proceeding 'Add' notification;" +
                                    "for client view consistency converted to 'Add': {}",
                            update
                    );
                }
                return new ChangeNotification<>(Kind.Add, update.getData());
        }
        return null;
    }
}
