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

package com.netflix.rx.eureka.server.audit.kafka;

import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.rx.eureka.client.resolver.ServerResolvers;
import com.netflix.rx.eureka.server.spi.ExtensionContext;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * This class creates {@link ServerList} with Kafka addresses that can be set
 * depending on the provided configuration either directly from a property value
 * ({@link KafkaAuditConfig#kafkaServerList}) or Eureka registry
 * ({@link KafkaAuditConfig#KAFKA_VIP_KEY}).
 *
 * @author Tomasz Bak
 */
public class KafkaServerListProvider implements Provider<ServerList<Server>> {

    private final ExtensionContext context;
    private final KafkaAuditConfig config;

    @Inject
    public KafkaServerListProvider(ExtensionContext context, KafkaAuditConfig config) {
        this.context = context;
        this.config = config;
    }

    @Override
    public ServerList<Server> get() {
        ServerList<Server> servers;
        if (config.getKafkaServerList() == null) {
            servers = new EurekaSourcedServerList(ServerResolvers.just(context.getInteralReadServerHost(),
                                                                       context.getInteralReadServerPort()),
                    config.getKafkaVip(),
                    config.getKafkaPort()
            );
        } else {
            servers = new PropertySourcedServerList(config.getKafkaServerList(), KafkaAuditConfig.KAFKA_PORT_DEFAULT);
        }
        return servers;
    }
}
