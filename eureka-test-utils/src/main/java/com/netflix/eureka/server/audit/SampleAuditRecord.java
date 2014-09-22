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

import com.netflix.eureka.interests.ChangeNotification.Kind;
import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.server.audit.AuditRecord.AuditRecordBuilder;

/**
 * @author Tomasz Bak
 */
public enum SampleAuditRecord {

    ZuulServerAdd() {
        @Override
        public AuditRecordBuilder builder() {
            return new AuditRecordBuilder()
                    .withTime(System.currentTimeMillis())
                    .withInstanceInfo(SampleInstanceInfo.ZuulServer.build())
                    .withModificationType(Kind.Add);
        }
    };

    public abstract AuditRecordBuilder builder();

    public AuditRecord build() {
        return builder().build();
    }
}
