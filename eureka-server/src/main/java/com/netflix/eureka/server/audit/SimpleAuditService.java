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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * Write audit records to a log.
 *
 * @author Tomasz Bak
 */
public class SimpleAuditService implements AuditService {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("com.netflix.eureka.auditLog");

    @Override
    public Observable<Void> write(AuditRecord record) {
        AUDIT_LOG.info("Audit log entry: {}", record);
        return Observable.empty();
    }
}
