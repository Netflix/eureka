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

import javax.inject.Inject;
import javax.inject.Provider;

import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.server.spi.ExtensionContext;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;

/**
 * This class creates {@link ServerList} with Kafka addresses that can be set
 * depending on the provided configuration either directly from a property value
 * ({@link KafkaAuditConfig#KAFKA_SERVER_LIST_KEY}) or Eureka registry
 * ({@link KafkaAuditConfig#KAFKA_VIP_KEY}).
 *
 * @author Tomasz Bak
 */
public class KafkaServerListProvider implements Provider<ServerList<Server>> {

    private final ExtensionContext context;
    private final KafkaAuditConfig config;

    @Inject
    public KafkaServerListProvider(ExtensionContext context) {
        this.context = context;
        this.config = new KafkaAuditConfig(context);
    }

    @Override
    public ServerList<Server> get() {
        ServerList<Server> servers;
        if (config.getKafkaServerList() == null) {
            servers = new EurekaSourcedServerList(
                    new StaticServerResolver<>(context.getInteralReadServerAddress()),
                    config.getKafkaVip(),
                    config.getKafkaPort()
            );
        } else {
            servers = new PropertySourcedServerList(config.getKafkaServerList(), KafkaAuditConfig.KAFKA_PORT_DEFAULT);
        }
        return servers;
    }
}
