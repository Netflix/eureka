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

package com.netflix.eureka.server.audit.suro;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.client.config.ClientConfigFactory.DefaultClientConfigFactory;
import com.netflix.eureka.server.audit.AuditRecord;
import com.netflix.eureka.server.audit.AuditService;
import com.netflix.eureka.server.spi.ExtensionContext;
import com.netflix.eureka.utils.Json;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.NoOpPing;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import com.netflix.suro.ClientConfig;
import com.netflix.suro.client.async.AsyncSuroClient;
import com.netflix.suro.client.async.Queue4Client;
import com.netflix.suro.connection.ConnectionPool;
import com.netflix.suro.message.Message;
import rx.Observable;

/**
 * {@link AuditService} implementation with persistence to Suro. {@link AuditRecord} is encoded
 * in JSON format. {@link ExtensionContext#eurekaClusterName()} is used as Suro routing key.
 *
 * @author Tomasz Bak
 */
@Singleton
public class SuroAuditService implements AuditService {

    private static final NoOpPing NO_OP_PING = new NoOpPing();

    private final ExtensionContext context;
    private final ServerList<Server> suroServerList;
    private volatile AsyncSuroClient suroClient;

    @Inject
    public SuroAuditService(ExtensionContext context, ServerList<Server> suroServerList) {
        this.context = context;
        this.suroServerList = suroServerList;
    }

    @PostConstruct
    public void start() {
        ClientConfig config = new ClientConfig();
        ILoadBalancer lb = new ZoneAwareLoadBalancer<Server>(
                DefaultClientConfigFactory.DEFAULT.newConfig(),
                new RoundRobinRule(),
                NO_OP_PING,
                suroServerList,
                null /* filter */
        );
        this.suroClient = new AsyncSuroClient(
                config,
                new Queue4Client(config),
                new ConnectionPool(config, lb)
        );
    }

    @PreDestroy
    public void stop() {
        if (suroClient != null) {
            suroClient.shutdown();
            suroClient = null;
        }
    }

    @Override
    public Observable<Void> write(AuditRecord record) {
        suroClient.send(new Message(context.eurekaClusterName(), Json.toByteArrayJson(record)));
        return Observable.empty();
    }
}
