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

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.EurekaRegistryImpl;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.Lease;
import rx.Observable;

/**
 * Registry decorator that provides audit logging.
 *
 * @author Tomasz Bak
 */
@Singleton
public class AuditedRegistry extends EurekaRegistryImpl {

    private final AuditService auditService;

    @Inject
    public AuditedRegistry(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        auditService.write(AuditRecords.forInstanceAdd(System.currentTimeMillis(), false, instanceInfo));
        return super.register(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        Lease<InstanceInfo> instanceToRemove = internalStore.get(instanceId);
        if (instanceToRemove != null) {
            auditService.write(AuditRecords.forInstanceDelete(System.currentTimeMillis(), false, instanceToRemove.getHolder()));
        }
        return super.unregister(instanceId);
    }

    @Override
    public Observable<Void> update(InstanceInfo updatedInfo, Set<Delta<?>> deltas) {
        auditService.write(AuditRecords.forInstanceUpdate(System.currentTimeMillis(), false, updatedInfo, deltas));
        return super.update(updatedInfo, deltas);
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> forInterest(Interest<InstanceInfo> interest) {
        return super.forInterest(interest);
    }

    @Override
    public Observable<Void> shutdown() {
        return super.shutdown();
    }
}
