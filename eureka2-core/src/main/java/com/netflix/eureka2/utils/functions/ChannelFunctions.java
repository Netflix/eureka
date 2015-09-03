package com.netflix.eureka2.utils.functions;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.model.notification.SourcedChangeNotification;
import com.netflix.eureka2.model.notification.SourcedModifyNotification;
import com.netflix.eureka2.model.notification.SourcedStreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.DeleteInstance;
import com.netflix.eureka2.protocol.common.InterestSetNotification;
import com.netflix.eureka2.protocol.common.StreamStateUpdate;
import com.netflix.eureka2.protocol.interest.UpdateInstanceInfo;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.slf4j.Logger;
import rx.Observable;
import rx.functions.Func1;

import java.util.Collections;
import java.util.Map;

/**
 * @author David Liu
 */
public final class ChannelFunctions {

    private final Logger logger;

    public ChannelFunctions(Logger loggerToUse) {
        this.logger = loggerToUse;
    }

    /**
     * Convert a channel message to the corresponding change notification. This method also takes an external String:InstanceInfo
     * cache that it updates for addition/modify/removals.
     */
    public ChangeNotification<InstanceInfo> channelMessageToNotification(InterestSetNotification message, Source source, Map<String, InstanceInfo> cache) {
        if (message instanceof AddInstance) {
            AddInstance msg = (AddInstance) message;
            InstanceInfo incoming = msg.getInstanceInfo();
            cache.put(incoming.getId(), incoming);
            return new SourcedChangeNotification<>(ChangeNotification.Kind.Add, incoming, source);
        } else if (message instanceof UpdateInstanceInfo) {
            UpdateInstanceInfo msg = (UpdateInstanceInfo) message;
            Delta<?> delta = msg.getDelta();
            InstanceInfo cached = cache.get(delta.getId());
            if (cached == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Update notification received for non-existent instance id " + delta.getId());
                }
                return null;
            } else {
                InstanceInfo updatedInfo = cached.applyDelta(delta);
                cache.put(updatedInfo.getId(), updatedInfo);
                return new SourcedModifyNotification<>(updatedInfo, Collections.<Delta<?>>singleton(delta), source);
            }
        } else if (message instanceof DeleteInstance) {
            DeleteInstance msg = (DeleteInstance) message;
            String instanceId = msg.getInstanceId();
            InstanceInfo removedInstance = cache.remove(instanceId);
            if (removedInstance == null) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Delete notification received for non-existent instance id " + instanceId);
                }
                return null;
            } else {
                return new SourcedChangeNotification<>(ChangeNotification.Kind.Delete, removedInstance, source);
            }
        } else if (message instanceof StreamStateUpdate) {
            StreamStateUpdate msg = (StreamStateUpdate) message;
            StreamStateNotification.BufferState state = msg.getState();
            if (state == StreamStateNotification.BufferState.BufferStart || state == StreamStateNotification.BufferState.BufferEnd) {
                return new SourcedStreamStateNotification<>(state, msg.getInterest(), source);
            } else {
                logger.warn("Unexpected state {}", state);
                return null;
            }
        } else {
            logger.warn("Unrecognised channel message {}", message);
            return null;
        }
    }

    /**
     * set up eviction of all previous channels' data once we see the buffer end coming back via the registry
     * interest subscription. The eviction source ids are used as generation counters
     */
    public Observable<Void> setUpPrevChannelEviction(final Source currentSource, final EurekaRegistry<InstanceInfo> registry) {
        final Source.SourceMatcher evictAllOlderMatcher = new Source.SourceMatcher() {
            @Override
            public boolean match(Source another) {
                if (another.getOrigin() == currentSource.getOrigin() &&
                        another.getName().equals(currentSource.getName()) &&
                        another.getId() < currentSource.getId()) {
                    return true;
                }
                return false;
            }
            @Override
            public String toString() {
                return "evictAllOlderMatcher{" + currentSource + "}";
            }
        };

        return registry.forInterest(Interests.forFullRegistry(), Source.matcherFor(currentSource))
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> changeNotification) {
                        if (changeNotification instanceof StreamStateNotification) {
                            StreamStateNotification<InstanceInfo> notification = (StreamStateNotification<InstanceInfo>) changeNotification;
                            if (notification.getBufferState() == StreamStateNotification.BufferState.BufferEnd) {
                                return true;
                            }
                        }
                        return false;
                    }
                })
                .take(1)
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<Long>>() {
                    @Override
                    public Observable<Long> call(ChangeNotification<InstanceInfo> changeNotification) {
                        return registry.evictAll(evictAllOlderMatcher);
                    }
                })
                .ignoreElements()
                .cast(Void.class);
    }
}
