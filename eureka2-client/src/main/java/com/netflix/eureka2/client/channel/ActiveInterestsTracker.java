package com.netflix.eureka2.client.channel;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.StreamState;
import com.netflix.eureka2.interests.StreamState.Kind;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * This class collects active interests, and their corresponding notification stream
 * state in the channel.
 *
 * @author Tomasz Bak
 */
public class ActiveInterestsTracker implements InterestSubscriptionStatus {

    private volatile MultipleInterests<InstanceInfo> activeInterests = new MultipleInterests<>();

    /**
     * Currently we observe only if we are in snapshot or live updates mode.
     */
    private final Set<Interest<InstanceInfo>> completedSnapshots =
            Collections.newSetFromMap(new ConcurrentHashMap<Interest<InstanceInfo>, Boolean>());

    public Interest<InstanceInfo> getActiveInterests() {
        if (activeInterests.getInterests().isEmpty()) {
            return Interests.forNone();
        }
        return activeInterests;
    }

    @Override
    public boolean isSnapshotCompleted() {
        return activeInterests.getInterests().size() == completedSnapshots.size();
    }

    @Override
    public boolean isSnapshotCompleted(Interest<InstanceInfo> interest) {
        if (interest instanceof MultipleInterests) {
            Set<Interest<InstanceInfo>> interests = ((MultipleInterests<InstanceInfo>) interest).getInterests();
            for (Interest<InstanceInfo> atomic : interests) {
                if (!completedSnapshots.contains(atomic)) {
                    return false;
                }
            }
            return true;
        }
        return completedSnapshots.contains(interest);
    }

    public void appendInterest(Interest<InstanceInfo> toAppend) {
        activeInterests = activeInterests.copyAndAppend(toAppend);
    }

    public void removeInterest(Interest<InstanceInfo> toRemove) {
        activeInterests = activeInterests.copyAndRemove(toRemove);
        completedSnapshots.retainAll(activeInterests.getInterests());
    }

    public void updateState(StreamStateNotification<InstanceInfo> stateNotification) {
        StreamState<InstanceInfo> streamState = stateNotification.getStreamState();
        if (streamState.getKind() == Kind.Live && activeInterests.getInterests().contains(streamState.getInterest())) {
            completedSnapshots.add(streamState.getInterest());
        }
    }
}
