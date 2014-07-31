package com.netflix.eureka.protocol.discovery;

import com.netflix.eureka.registry.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public class AddInstance {
    private final InstanceInfo instanceInfo;

    // For serialization frameworks
    protected AddInstance() {
        instanceInfo = null;
    }

    public AddInstance(InstanceInfo instanceInfo) {
        this.instanceInfo = instanceInfo;
    }

    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AddInstance that = (AddInstance) o;

        if (instanceInfo != null ? !instanceInfo.equals(that.instanceInfo) : that.instanceInfo != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return instanceInfo != null ? instanceInfo.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "AddInstance{instanceInfo=" + instanceInfo + '}';
    }
}
