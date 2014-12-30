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

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ModifyNotification;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.audit.AuditRecord.AuditRecordBuilder;

import java.util.Set;

/**
 * @author Tomasz Bak
 */
public final class AuditRecords {

    private AuditRecords() {
    }

    public static AuditRecord forInstanceAdd(String auditServerId, long timestamp, boolean userTriggered, InstanceInfo newInstanceInfo) {
        return withValues(auditServerId, timestamp, userTriggered, newInstanceInfo).withModificationType(Kind.Add).build();
    }

    public static AuditRecord forInstanceUpdate(String auditServerId, long timestamp, boolean userTriggered, InstanceInfo newInstanceInfo, Set<Delta<?>> delta) {
        return withValues(auditServerId, timestamp, userTriggered, newInstanceInfo).withModificationType(Kind.Modify).withDeltas(delta).build();
    }

    public static AuditRecord forInstanceDelete(String auditServerId, long timestamp, boolean userTriggered, InstanceInfo toRemove) {
        return withValues(auditServerId, timestamp, userTriggered, toRemove).withModificationType(Kind.Delete).build();
    }

    public static AuditRecord forChangeNotification(String auditServerId, long timestamp, boolean userTriggered, ChangeNotification<InstanceInfo> changeNotification) {
        switch (changeNotification.getKind()) {
            case Add:
                return forInstanceAdd(auditServerId, timestamp, userTriggered, changeNotification.getData());
            case Modify:
                ModifyNotification<InstanceInfo> modifyNotification = (ModifyNotification<InstanceInfo>) changeNotification;
                return forInstanceUpdate(auditServerId, timestamp, userTriggered, modifyNotification.getData(), modifyNotification.getDelta());
            case Delete:
                return forInstanceDelete(auditServerId, timestamp, userTriggered, changeNotification.getData());
        }
        throw new IllegalStateException("unhadled enum value " + changeNotification.getKind());
    }

    private static AuditRecordBuilder withValues(String auditServerId, long timestamp, boolean userTriggered, InstanceInfo newInstanceInfo) {
        return new AuditRecordBuilder()
                .withAuditServerId(auditServerId)
                .withTime(timestamp)
                .withUserTriggered(userTriggered)
                .withInstanceInfo(newInstanceInfo);
    }
}
