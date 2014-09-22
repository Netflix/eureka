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

package com.netflix.eureka.server.audit;

import java.util.Set;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.ChangeNotification.Kind;
import com.netflix.eureka.interests.ModifyNotification;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.audit.AuditRecord.AuditRecordBuilder;

/**
 * @author Tomasz Bak
 */
public final class AuditRecords {

    private AuditRecords() {
    }

    public static AuditRecord forInstanceAdd(long timestamp, boolean userTriggered, InstanceInfo newInstanceInfo) {
        return withValues(timestamp, userTriggered, newInstanceInfo).withModificationType(Kind.Add).build();
    }

    public static AuditRecord forInstanceUpdate(long timestamp, boolean userTriggered, InstanceInfo newInstanceInfo, Set<Delta<?>> delta) {
        return withValues(timestamp, userTriggered, newInstanceInfo).withModificationType(Kind.Modify).withDeltas(delta).build();
    }

    public static AuditRecord forInstanceDelete(long timestamp, boolean userTriggered, InstanceInfo toRemove) {
        return withValues(timestamp, userTriggered, toRemove).withModificationType(Kind.Delete).build();
    }

    public static AuditRecord forChangeNotification(long timestamp, boolean userTriggered, ChangeNotification<InstanceInfo> changeNotification) {
        switch (changeNotification.getKind()) {
            case Add:
                return forInstanceAdd(timestamp, userTriggered, changeNotification.getData());
            case Modify:
                ModifyNotification<InstanceInfo> modifyNotification = (ModifyNotification<InstanceInfo>) changeNotification;
                return forInstanceUpdate(timestamp, userTriggered, modifyNotification.getData(), modifyNotification.getDelta());
            case Delete:
                return forInstanceDelete(timestamp, userTriggered, changeNotification.getData());
        }
        throw new IllegalStateException("unhadled enum value " + changeNotification.getKind());
    }

    private static AuditRecordBuilder withValues(long timestamp, boolean userTriggered, InstanceInfo newInstanceInfo) {
        return new AuditRecordBuilder()
                .withTime(timestamp)
                .withUserTriggered(userTriggered)
                .withInstanceInfo(newInstanceInfo);
    }
}
