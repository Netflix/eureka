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

package com.netflix.eureka.server.transport.tcp.replication;

import com.google.inject.Inject;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.server.service.EurekaServerService;
import com.netflix.eureka.server.service.EurekaServiceImpl;
import com.netflix.eureka.server.transport.ClientConnectionImpl;
import com.netflix.eureka.transport.base.BaseMessageBroker;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class TcpReplicationHandler implements ConnectionHandler<Object, Object> {

    private final EurekaRegistry registry;

    @Inject
    public TcpReplicationHandler(EurekaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        final ClientConnectionImpl eurekaConn = new ClientConnectionImpl(new BaseMessageBroker(connection));
        final EurekaServerService service = new EurekaServiceImpl(registry, eurekaConn);
        return service.newReplicationChannel().asLifecycleObservable();
    }
}
