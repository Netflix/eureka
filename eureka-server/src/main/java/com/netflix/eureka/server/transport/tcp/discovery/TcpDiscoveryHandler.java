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

package com.netflix.eureka.server.transport.tcp.discovery;

import javax.inject.Inject;

import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.server.service.EurekaServerService;
import com.netflix.eureka.server.service.EurekaServiceImpl;
import com.netflix.eureka.server.transport.ClientConnectionImpl;
import com.netflix.eureka.transport.base.BaseMessageBroker;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;

/**
 * @author Tomasz Bak
 */
public class TcpDiscoveryHandler implements ConnectionHandler<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(TcpDiscoveryHandler.class);

    private final EurekaRegistry registry;

    @Inject
    public TcpDiscoveryHandler(EurekaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        if (logger.isDebugEnabled()) {
            logger.debug("New TCP dscovery client connection");
        }
        final ClientConnectionImpl eurekaConn = new ClientConnectionImpl(new BaseMessageBroker(connection));
        final EurekaServerService service = new EurekaServiceImpl(registry, eurekaConn);
        // Since this is a discovery handler which only handles interest subscriptions,
        // the channel is created on connection accept. We subscribe here for the sake
        // of logging only.
        Observable<Void> lifecycleObservable = service.newInterestChannel().asLifecycleObservable();
        lifecycleObservable.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Connection from client terminated");
            }

            @Override
            public void onError(Throwable e) {
                logger.info("Connection from client terminated with error");
                logger.debug("Connection exception", e);
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });
        return lifecycleObservable;
    }
}
