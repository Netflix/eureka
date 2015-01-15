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

package com.netflix.eureka2.server.audit.kafka;

import java.net.InetSocketAddress;

import com.google.inject.TypeLiteral;
import com.netflix.eureka2.server.audit.AuditService;
import com.netflix.eureka2.server.spi.ExtAbstractModule;
import com.netflix.eureka2.server.spi.ExtensionLoader.StandardExtension;
import com.netflix.eureka2.utils.StreamedDataCollector;

/**
 * Guice module for injecting {@link AuditService} with Kafka persistence.
 *
 * @author Tomasz Bak
 */
public class KafkaAuditServiceModule extends ExtAbstractModule {

    private static final TypeLiteral<StreamedDataCollector<InetSocketAddress>> STREAMED_DATA_COLLECTOR_TYPE_LITERAL =
            new TypeLiteral<StreamedDataCollector<InetSocketAddress>>() {
            };

    @Override
    protected void configure() {
        bind(STREAMED_DATA_COLLECTOR_TYPE_LITERAL).toProvider(KafkaServersProvider.class);
        bind(AuditService.class).to(KafkaAuditService.class);
    }

    @Override
    public StandardExtension standardExtension() {
        return StandardExtension.AuditServiceExt;
    }
}
