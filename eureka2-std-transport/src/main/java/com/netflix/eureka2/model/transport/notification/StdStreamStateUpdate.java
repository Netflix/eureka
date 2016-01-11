/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.model.transport.notification;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.model.notification.StreamStateNotification.BufferState;
import com.netflix.eureka2.spi.model.transport.notification.StreamStateUpdate;

/**
 */
public class StdStreamStateUpdate implements StreamStateUpdate {

    private final BufferState state;

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = As.PROPERTY, property = "class")
    private final Interest<InstanceInfo> interest;

    /* For reflection */
    protected StdStreamStateUpdate() {
        state = null;
        interest = null;
    }

    public StdStreamStateUpdate(StreamStateNotification<InstanceInfo> stateNotification) {
        this.state = stateNotification.getBufferState();
        this.interest = stateNotification.getInterest();
    }

    public BufferState getState() {
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

        StdStreamStateUpdate that = (StdStreamStateUpdate) o;

        if (state != that.state)
            return false;
        if (interest != null ? !interest.equals(that.interest) : that.interest != null)
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
        return "StdStreamStateUpdate{state=" + state + ", interest=" + interest + '}';
    }
}
