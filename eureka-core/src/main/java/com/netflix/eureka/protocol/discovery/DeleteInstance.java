package com.netflix.eureka.protocol.discovery;

/**
 * @author Tomasz Bak
 */
public class DeleteInstance {

    private final String instanceId;

    // For serialization frameworks
    protected DeleteInstance() {
        instanceId = null;
    }

    public DeleteInstance(String instanceId) {
        this.instanceId = instanceId;
    }

    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DeleteInstance that = (DeleteInstance) o;

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
        return "DeleteInstance{instanceId='" + instanceId + '\'' + '}';
    }
}
