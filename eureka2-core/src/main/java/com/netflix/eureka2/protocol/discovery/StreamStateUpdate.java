package com.netflix.eureka2.protocol.discovery;

import com.netflix.eureka2.interests.StreamState;
import com.netflix.eureka2.registry.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public class StreamStateUpdate implements InterestSetNotification {
    private final StreamState<InstanceInfo> state;

    protected StreamStateUpdate() {
        state = null;
    }

    public StreamStateUpdate(StreamState<InstanceInfo> state) {
        this.state = state;
    }

    public StreamState<InstanceInfo> getState() {
        return state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        StreamStateUpdate that = (StreamStateUpdate) o;

        if (state != null ? !state.equals(that.state) : that.state != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        return state != null ? state.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "StreamStateUpdate{" +
                "state=" + state +
                '}';
    }
}
