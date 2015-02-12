package com.netflix.eureka2.protocol.discovery;

import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.interests.StreamStateNotification.BufferingState;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public class StreamStateUpdate implements InterestSetNotification {

    private final BufferingState state;
    private final Interest<InstanceInfo> interest;

    /* For reflection */
    protected StreamStateUpdate() {
        state = null;
        interest = null;
    }

    public StreamStateUpdate(StreamStateNotification<InstanceInfo> stateNotification) {
        this.state = stateNotification.getBufferingState();
        this.interest = stateNotification.getInterest();
    }

    public BufferingState getState() {
        return state;
    }

    public Interest<InstanceInfo> getInterest() {
        return interest;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        StreamStateUpdate that = (StreamStateUpdate) o;

        if (interest != null ? !interest.equals(that.interest) : that.interest != null)
            return false;
        if (state != that.state)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = state != null ? state.hashCode() : 0;
        result = 31 * result + (interest != null ? interest.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "StreamStateUpdate{state=" + state + ", interest=" + interest + '}';
    }
}
