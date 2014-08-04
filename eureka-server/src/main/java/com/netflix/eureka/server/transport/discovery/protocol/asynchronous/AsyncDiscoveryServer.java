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

package com.netflix.eureka.server.transport.discovery.protocol.asynchronous;

import com.netflix.eureka.server.transport.Context;
import com.netflix.eureka.server.transport.TransportServer;
import com.netflix.eureka.server.transport.discovery.DiscoveryHandler;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import rx.functions.Action1;

/**
 * @author Tomasz Bak
 */
public class AsyncDiscoveryServer implements TransportServer {

    private final MessageBrokerServer brokerServer;

    public AsyncDiscoveryServer(MessageBrokerServer brokerServer, final DiscoveryHandler handler) {
        this.brokerServer = brokerServer;
        brokerServer.clientConnections().forEach(new Action1<MessageBroker>() {
            @Override
            public void call(MessageBroker messageBroker) {
                // FIXME What is the best way to identify active client connection?
                Context clientContext = new Context();
                handler.updates(clientContext);
                messageBroker.incoming().forEach(new ClientMessageDispatcher());
            }
        });
    }

    @Override
    public void shutdown() throws InterruptedException {
        brokerServer.shutdown();
    }

    static class ClientMessageDispatcher implements Action1<Message> {

        @Override
        public void call(Message message) {
        }
    }
}
