package com.netflix.eureka2.registry.index;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Sourced;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification.BufferState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.netflix.eureka2.utils.ExtCollections.concat;

/**
 * An {@link Index.InitStateHolder} implementation for {@link InstanceInfo}.
 * As the cached state is backed by {@link ConcurrentHashMap}, the order of notifications
 * is not preserved, which is fine as there is exactly one item per {@link InstanceInfo} object.
 *
 * @author Nitesh Kant
 */
public class InstanceInfoInitStateHolder extends Index.InitStateHolder<InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(InstanceInfoInitStateHolder.class);

    protected final ConcurrentMap<String, ChangeNotification<InstanceInfo>> notificationMap;

    protected final String defaultSourceKey = Source.Origin.LOCAL.name();
    protected final ConcurrentMap<String, ChangeNotification<InstanceInfo>> bufferStarts;
    protected final ConcurrentMap<String, ChangeNotification<InstanceInfo>> bufferEnds;

    protected final ChangeNotification<InstanceInfo> bufferStartNotification;
    protected final ChangeNotification<InstanceInfo> bufferEndNotification;
    protected final ChangeNotification<InstanceInfo> bufferUnknownNotification;

    public InstanceInfoInitStateHolder(Iterator<ChangeNotification<InstanceInfo>> initialRegistry, Interest<InstanceInfo> interest) {
        this.bufferStartNotification = new StreamStateNotification<>(BufferState.BufferStart, interest);
        this.bufferEndNotification = new StreamStateNotification<>(BufferState.BufferEnd, interest);
        this.bufferUnknownNotification = new StreamStateNotification<>(BufferState.Unknown, interest);

        notificationMap = new ConcurrentHashMap<>();
        bufferStarts = new ConcurrentHashMap<>();
        bufferEnds = new ConcurrentHashMap<>();

        while (initialRegistry.hasNext()) {
            addNotification(initialRegistry.next());
        }
    }

    @Override
    public void addNotification(ChangeNotification<InstanceInfo> notification) {
        if (notification.isDataNotification()) {
            String id = notification.getData().getId();
            ChangeNotification<InstanceInfo> current = notificationMap.get(id);

            ChangeNotification<InstanceInfo> updated = processNext(current, notification);
            if (updated != null) {
                notificationMap.put(id, updated);
            } else if (current != null) {
                notificationMap.remove(id);
            }
        } else if (notification instanceof StreamStateNotification) {
            StreamStateNotification<InstanceInfo> stateNotification = (StreamStateNotification<InstanceInfo>) notification;
            String sourceKey = defaultSourceKey;
            if (notification instanceof Sourced) {
                sourceKey = getSourceKey(((Sourced) notification).getSource());
            } else {
                // can remove after we verify
                logger.warn("No source available for the notification {}", notification);
            }
            switch (stateNotification.getBufferState()) {
                case BufferStart:
                    bufferStarts.put(sourceKey, notification);
                    bufferEnds.remove(sourceKey);
                    break;
                case BufferEnd:
                    bufferEnds.put(sourceKey, notification);
                    break;
                default:
                    // not possible
            }
        } // else, no-op
    }

    @Override
    public void clearAllNotifications() {
        notificationMap.clear();
    }

    @Override
    public Iterator<ChangeNotification<InstanceInfo>> _newIterator() {
        return concat(
                bufferStarts.values().iterator(),
                notificationMap.values().iterator(),
                bufferEnds.values().iterator()
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
                            "for client view consistency converted to 'Add': {}", update
                    );
                }

                // need to turn the modify notification to an add notification (for the init state holder)
                if (update instanceof Sourced) {
                    return new SourcedChangeNotification<>(Kind.Add, update.getData(), ((Sourced) update).getSource());
                } else {
                    return new ChangeNotification<>(Kind.Add, update.getData());
                }
        }
        return null;
    }

    // compute the key to use to store streamstate notifications in the buffer marker hashmaps
    // for LOCAL, it's just the origin type.
    // for others, it's a combination of the origin type plus the name (but not the id)
    private static String getSourceKey(Source source) {
        switch (source.getOrigin()) {
            case LOCAL:
                return source.getOrigin().name();
            default:
                return source.getOriginNamePair();
        }
    }
}
