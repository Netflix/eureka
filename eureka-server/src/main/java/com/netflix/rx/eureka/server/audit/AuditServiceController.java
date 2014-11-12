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

package com.netflix.rx.eureka.server.audit;

import com.netflix.rx.eureka.interests.ChangeNotification;
import com.netflix.rx.eureka.interests.Interests;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author Tomasz Bak
 */
@Singleton
public class AuditServiceController {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceController.class);

    private final EurekaServerRegistry<InstanceInfo> registry;
    private final AuditService auditService;

    @Inject
    public AuditServiceController(EurekaServerRegistry registry, AuditService auditService) {
        this.registry = registry;
        this.auditService = auditService;
    }

    @PostConstruct
    public void startRegistryAuditing() {
        // TODO: this should be only Origin.Local, but since bridge works on replication channel we would not audit eureka 1.0 entries.
        registry.forInterest(Interests.forFullRegistry()).subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                logger.warn("Registry auditing finished");
            }

            @Override
            public void onError(Throwable e) {
                logger.warn("Registry auditing finished due to an error in interest channel subscription", e);
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                AuditRecord record = AuditRecords.forChangeNotification(System.currentTimeMillis(), false, notification);
                auditService.write(record);
            }
        });
    }
}
