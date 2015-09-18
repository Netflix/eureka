package com.netflix.eureka.cluster.protocol;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka.registry.PeerAwareInstanceRegistryImpl.Action;

/**
 * The jersey resource class that generates a particular replication event
 */
public class ReplicationInstance {
    private String appName;
    private String id;
    private Long lastDirtyTimestamp;
    private String overriddenStatus;
    private String status;
    private InstanceInfo instanceInfo;
    private Action action;

    @JsonCreator
    public ReplicationInstance(@JsonProperty("appName") String appName,
                               @JsonProperty("id") String id,
                               @JsonProperty("lastDirtyTimestamp") Long lastDirtyTimestamp,
                               @JsonProperty("overriddenStatus") String overriddenStatus,
                               @JsonProperty("status") String status,
                               @JsonProperty("instanceInfo") InstanceInfo instanceInfo,
                               @JsonProperty("action") Action action) {
        this.appName = appName;
        this.id = id;
        this.lastDirtyTimestamp = lastDirtyTimestamp;
        this.overriddenStatus = overriddenStatus;
        this.status = status;
        this.instanceInfo = instanceInfo;
        this.action = action;
    }

    public String getAppName() {
        return appName;
    }

    public String getId() {
        return id;
    }

    public Long getLastDirtyTimestamp() {
        return lastDirtyTimestamp;
    }

    public String getOverriddenStatus() {
        return overriddenStatus;
    }

    public String getStatus() {
        return status;
    }

    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    public Action getAction() {
        return action;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        ReplicationInstance that = (ReplicationInstance) o;

        if (appName != null ? !appName.equals(that.appName) : that.appName != null)
            return false;
        if (id != null ? !id.equals(that.id) : that.id != null)
            return false;
        if (lastDirtyTimestamp != null ? !lastDirtyTimestamp.equals(that.lastDirtyTimestamp) : that.lastDirtyTimestamp != null)
            return false;
        if (overriddenStatus != null ? !overriddenStatus.equals(that.overriddenStatus) : that.overriddenStatus != null)
            return false;
        if (status != null ? !status.equals(that.status) : that.status != null)
            return false;
        if (instanceInfo != null ? !instanceInfo.equals(that.instanceInfo) : that.instanceInfo != null)
            return false;
        return action == that.action;

    }

    @Override
    public int hashCode() {
        int result = appName != null ? appName.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (lastDirtyTimestamp != null ? lastDirtyTimestamp.hashCode() : 0);
        result = 31 * result + (overriddenStatus != null ? overriddenStatus.hashCode() : 0);
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (instanceInfo != null ? instanceInfo.hashCode() : 0);
        result = 31 * result + (action != null ? action.hashCode() : 0);
        return result;
    }

    public static ReplicationInstanceBuilder replicationInstance() {
        return ReplicationInstanceBuilder.aReplicationInstance();
    }

    public static class ReplicationInstanceBuilder {
        private String appName;
        private String id;
        private Long lastDirtyTimestamp;
        private String overriddenStatus;
        private String status;
        private InstanceInfo instanceInfo;
        private Action action;

        private ReplicationInstanceBuilder() {
        }

        public static ReplicationInstanceBuilder aReplicationInstance() {
            return new ReplicationInstanceBuilder();
        }

        public ReplicationInstanceBuilder withAppName(String appName) {
            this.appName = appName;
            return this;
        }

        public ReplicationInstanceBuilder withId(String id) {
            this.id = id;
            return this;
        }

        public ReplicationInstanceBuilder withLastDirtyTimestamp(Long lastDirtyTimestamp) {
            this.lastDirtyTimestamp = lastDirtyTimestamp;
            return this;
        }

        public ReplicationInstanceBuilder withOverriddenStatus(String overriddenStatus) {
            this.overriddenStatus = overriddenStatus;
            return this;
        }

        public ReplicationInstanceBuilder withStatus(String status) {
            this.status = status;
            return this;
        }

        public ReplicationInstanceBuilder withInstanceInfo(InstanceInfo instanceInfo) {
            this.instanceInfo = instanceInfo;
            return this;
        }

        public ReplicationInstanceBuilder withAction(Action action) {
            this.action = action;
            return this;
        }

        public ReplicationInstanceBuilder but() {
            return aReplicationInstance().withAppName(appName).withId(id).withLastDirtyTimestamp(lastDirtyTimestamp).withOverriddenStatus(overriddenStatus).withStatus(status).withInstanceInfo(instanceInfo).withAction(action);
        }

        public ReplicationInstance build() {
            return new ReplicationInstance(
                    appName,
                    id,
                    lastDirtyTimestamp,
                    overriddenStatus,
                    status,
                    instanceInfo,
                    action
            );
        }
    }
}
