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

import java.util.Arrays;

import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.protocol.discovery.InterestSetNotification;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.server.transport.Context;
import com.netflix.eureka.server.transport.TransportServer;
import com.netflix.eureka.server.transport.discovery.DiscoveryHandler;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import rx.Subscriber;
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
                handler.updates(clientContext).forEach(new NotificationForwarded(messageBroker));
                messageBroker.incoming().forEach(new ClientMessageDispatcher(messageBroker, clientContext, handler));
            }
        });
    }

    @Override
    public void shutdown() throws InterruptedException {
        brokerServer.shutdown();
    }

    static class ClientMessageDispatcher implements Action1<Message> {

        private final MessageBroker messageBroker;
        private final Context clientContext;
        private final DiscoveryHandler handler;

        ClientMessageDispatcher(MessageBroker messageBroker, Context clientContext, DiscoveryHandler handler) {
            this.messageBroker = messageBroker;
            this.clientContext = clientContext;
            this.handler = handler;
        }

        @Override
        public void call(final Message message) {
            if (!(message instanceof UserContentWithAck)) {
                return;
            }
            Subscriber<Void> ackSubscriber = new Subscriber<Void>() {
                @Override
                public void onCompleted() {
                    messageBroker.acknowledge((UserContentWithAck) message);
                }

                @Override
                public void onError(Throwable e) {
                }

                @Override
                public void onNext(Void aVoid) {
                }
            };
            Object content = ((UserContent) message).getContent();
            if (content instanceof RegisterInterestSet) {
                Interest[] interests = ((RegisterInterestSet) content).getInterestSet();
                handler.registerInterestSet(clientContext, Arrays.asList(interests)).subscribe(ackSubscriber);
            } else if (content instanceof UnregisterInterestSet) {
                handler.unregisterInterestSet(clientContext).subscribe(ackSubscriber);
            }
        }
    }

    static class NotificationForwarded implements Action1<InterestSetNotification> {
        private final MessageBroker messageBroker;

        NotificationForwarded(MessageBroker messageBroker) {
            this.messageBroker = messageBroker;
        }

        @Override
        public void call(InterestSetNotification notification) {
            messageBroker.submit(new UserContent(notification));
        }
    }
}
