package com.netflix.eureka.interests;

import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author Nitesh Kant
 */
public class InstanceInterest extends Interest<InstanceInfo> {

    private final String instanceId;

    protected InstanceInterest() {
        instanceId = null;
    }

    public InstanceInterest(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean matches(InstanceInfo data) {
        return instanceId.equals(data.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InstanceInterest that = (InstanceInterest) o;

        if (instanceId != null ? !instanceId.equals(that.instanceId) : that.instanceId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return instanceId != null ? instanceId.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "InstanceInterest{instanceId='" + instanceId + '\'' + '}';
    }
}
