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

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Provider;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class AuditServiceController {

    private static final Logger logger = LoggerFactory.getLogger(AuditServiceController.class);

    private final EurekaRegistry<InstanceInfo> registry;
    private final AuditService auditService;
    private final Provider<SelfInfoResolver> serverIdentity;

    @Inject
    public AuditServiceController(
            EurekaRegistry registry,
            AuditService auditService,
            Provider<SelfInfoResolver> serverIdentity) {
        this.registry = registry;
        this.auditService = auditService;
        this.serverIdentity = serverIdentity;
    }

    @PostConstruct
    public void startRegistryAuditing() {
        // TODO: this should be only Origin.Local, but since bridge works on replication channel we would not audit eureka 1.0 entries.
        serverIdentity.get().resolve().take(1).flatMap(new Func1<InstanceInfo, Observable<Void>>() {
            @Override
            public Observable<Void> call(InstanceInfo ownInstanceInfo) {
                final String ownServerId = ownInstanceInfo == null ? null : ownInstanceInfo.getId();
                return registry.forInterest(Interests.forFullRegistry()).doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.isDataNotification()) {
                            AuditRecord record = AuditRecords.forChangeNotification(ownServerId, System.currentTimeMillis(), false, notification);
                            auditService.write(record);
                        }
                    }
                }).ignoreElements().cast(Void.class);
            }
        }).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.warn("Registry auditing finished");
            }

            @Override
            public void onError(Throwable e) {
                logger.warn("Registry auditing finished due to an error in interest channel subscription", e);
            }

            @Override
            public void onNext(Void notification) {
            }
        });
    }
}
