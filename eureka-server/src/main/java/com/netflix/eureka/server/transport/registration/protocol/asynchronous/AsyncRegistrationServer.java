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

package com.netflix.eureka.server.transport.registration.protocol.asynchronous;

import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.transport.Context;
import com.netflix.eureka.server.transport.TransportServer;
import com.netflix.eureka.server.transport.registration.RegistrationHandler;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class AsyncRegistrationServer implements TransportServer {

    private final MessageBrokerServer brokerServer;

    public AsyncRegistrationServer(MessageBrokerServer brokerServer, final RegistrationHandler handler) {
        this.brokerServer = brokerServer;
        brokerServer.clientConnections().doOnTerminate(new Action0() {
            @Override
            public void call() {
                try {
                    shutdown();
                } catch (InterruptedException e) {
                    // IGNORE
                }
            }
        }).forEach(new Action1<MessageBroker>() {
            @Override
            public void call(final MessageBroker messageBroker) {
                // FIXME What is the best way to identify active client connection?
                Context clientContext = new Context();
                messageBroker.incoming().flatMap(new ClientMessageDispatcher(messageBroker, clientContext, handler))
                        .subscribe(new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                                messageBroker.shutdown();
                            }

                            @Override
                            public void onError(Throwable e) {
                            }

                            @Override
                            public void onNext(Void aVoid) {
                                messageBroker.shutdown();
                            }
                        });
            }
        });
    }

    @Override
    public void shutdown() throws InterruptedException {
        brokerServer.shutdown();
    }

    static class ClientMessageDispatcher implements Func1<Object, Observable<Void>> {

        private final MessageBroker messageBroker;
        private final Context clientContext;
        private final RegistrationHandler handler;

        ClientMessageDispatcher(MessageBroker messageBroker, Context clientContext, RegistrationHandler handler) {
            this.messageBroker = messageBroker;
            this.clientContext = clientContext;
            this.handler = handler;
        }

        @Override
        public Observable<Void> call(final Object message) {
            Observable<Void> response;
            if (message instanceof Register) {
                response = handler.register(clientContext, ((Register) message).getInstanceInfo());
            } else if (message instanceof Unregister) {
                response = handler.unregister(clientContext);
            } else if (message instanceof Update) {
                response = handler.update(clientContext, (Update) message);
            } else if (message instanceof Heartbeat) {
                return handler.heartbeat(clientContext);
            } else {
                return Observable.empty();
            }
            return response.doOnCompleted(new Action0() {
                @Override
                public void call() {
                    messageBroker.acknowledge(message);
                }
            });
        }
    }

}
