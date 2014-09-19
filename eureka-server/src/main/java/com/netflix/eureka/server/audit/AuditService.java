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

import rx.Observable;

/**
 * All registry modifications can be persisted for auditing in an external system of choice
 * by implementing this interface.
 *
 * <h1>Thread safety</h1>
 * Registry updates can be done concurrently, hance {@link AuditService} must be thread safe.
 *
 * <h1>Error handling</h1>
 * {@link AuditService#write(AuditRecord)} returns observable of void that should complete
 * with error, if the underlying implementation cannot fullfil the request. This
 * information is ignored by Eureka when writing audit logs, but can be used by
 * implementations to provide fallback storage.
 *
 * @author Tomasz Bak
 */
public interface AuditService {

    /**
     * Write an audit record.
     *
     * @return observable that completes immediately or propagates an error, if the
     *         underlying audit service implementation cannot fullfil the request
     */
    Observable<Void> write(AuditRecord record);
}
