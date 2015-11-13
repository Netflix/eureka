/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.server.audit;

import java.util.Set;

import com.netflix.eureka2.model.instance.Delta;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;

/**
 * Audit record for Eureka registry changes.
 *
 * @author Tomasz Bak
 */
public class AuditRecord {

    /**
     * id of logging audit server
     */
    private final String auditServerId;

    /**
     * Time of applying the change to a registry.
     */
    private final long time;

    /**
     * true if this event happened due to a request by the user.
     */
    private final boolean userTriggered;

    /**
     * Registry modification type. See {@link ChangeNotification}.
     */
    private final ChangeNotification.Kind modificationType;

    private final InstanceInfo instanceInfo;

    private final Set<Delta<?>> deltas;

    protected AuditRecord(String auditServerId, long time, boolean userTriggered, Kind modificationType,
                          InstanceInfo instanceInfo, Set<Delta<?>> deltas) {
        this.auditServerId = auditServerId;
        this.time = time;
        this.userTriggered = userTriggered;
        this.modificationType = modificationType;
        this.instanceInfo = instanceInfo;
        this.deltas = deltas;
    }

    public String getAuditServerId() {
        return auditServerId;
    }

    public InstanceInfo getInstanceInfo() {
        return instanceInfo;
    }

    public long getTime() {
        return time;
    }

    public boolean isUserTriggered() {
        return userTriggered;
    }

    public Kind getModificationType() {
        return modificationType;
    }

    public Set<Delta<?>> getDeltas() {
        return deltas;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AuditRecord))
            return false;

        AuditRecord that = (AuditRecord) o;

        if (time != that.time)
            return false;
        if (userTriggered != that.userTriggered)
            return false;
        if (auditServerId != null ? !auditServerId.equals(that.auditServerId) : that.auditServerId != null)
            return false;
        if (deltas != null ? !deltas.equals(that.deltas) : that.deltas != null)
            return false;
        if (instanceInfo != null ? !instanceInfo.equals(that.instanceInfo) : that.instanceInfo != null)
            return false;
        if (modificationType != that.modificationType)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = auditServerId != null ? auditServerId.hashCode() : 0;
        result = 31 * result + (int) (time ^ (time >>> 32));
        result = 31 * result + (userTriggered ? 1 : 0);
        result = 31 * result + (modificationType != null ? modificationType.hashCode() : 0);
        result = 31 * result + (instanceInfo != null ? instanceInfo.hashCode() : 0);
        result = 31 * result + (deltas != null ? deltas.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "AuditRecord{" +
                "auditServerId='" + auditServerId + '\'' +
                ", time=" + time +
                ", userTriggered=" + userTriggered +
                ", modificationType=" + modificationType +
                ", instanceInfo=" + instanceInfo +
                ", deltas=" + deltas +
                '}';
    }

    public static class AuditRecordBuilder {
        private String auditServerId;
        private long time;
        private boolean userTriggered;
        private Kind modificationType;
        private InstanceInfo instanceInfo;
        private Set<Delta<?>> deltas;

        public static AuditRecordBuilder anAuditRecord() {
            return new AuditRecordBuilder();
        }

        public AuditRecordBuilder withAuditServerId(String auditServerId) {
            this.auditServerId = auditServerId;
            return this;
        }

        public AuditRecordBuilder withTime(long time) {
            this.time = time;
            return this;
        }

        public AuditRecordBuilder withUserTriggered(boolean userTriggered) {
            this.userTriggered = userTriggered;
            return this;
        }

        public AuditRecordBuilder withModificationType(Kind modificationType) {
            this.modificationType = modificationType;
            return this;
        }

        public AuditRecordBuilder withInstanceInfo(InstanceInfo instanceInfo) {
            this.instanceInfo = instanceInfo;
            return this;
        }

        public AuditRecordBuilder withDeltas(Set<Delta<?>> deltas) {
            this.deltas = deltas;
            return this;
        }

        public AuditRecord build() {
            return new AuditRecord(auditServerId, time, userTriggered, modificationType, instanceInfo, deltas);
        }
    }
}
