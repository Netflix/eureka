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

package com.netflix.eureka.server.audit.kafka;

import com.google.inject.TypeLiteral;
import com.netflix.eureka.server.audit.AuditService;
import com.netflix.eureka.server.spi.ExtAbstractModule;
import com.netflix.eureka.server.spi.ExtensionContext;
import com.netflix.eureka.server.spi.ExtensionLoader.StandardExtension;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

/**
 * Guice module for injecting {@link AuditService} with suro persistence.
 *
 * @author Tomasz Bak
 */
public class KafkaAuditServiceModule extends ExtAbstractModule {

    private static final TypeLiteral<ServerList<Server>> SERVER_LIST_TYPE_LITERAL = new TypeLiteral<ServerList<Server>>() {
    };

    @Override
    protected void configure() {
        bind(SERVER_LIST_TYPE_LITERAL).toProvider(KafkaServerListProvider.class);
        bind(AuditService.class).to(KafkaAuditService.class);
    }

    @Override
    public boolean isRunnable(ExtensionContext extensionContext) {
        try {
            new KafkaAuditConfig(extensionContext);
        } catch (Exception ex) {
            return false;
        }
        return true;
    }

    @Override
    public StandardExtension standardExtension() {
        return StandardExtension.AuditServiceExt;
    }
}
