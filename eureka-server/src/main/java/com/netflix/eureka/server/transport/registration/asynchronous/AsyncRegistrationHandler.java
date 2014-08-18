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

package com.netflix.eureka.server.transport.registration.asynchronous;

import java.util.concurrent.atomic.AtomicReference;

import com.google.inject.Inject;
import com.netflix.eureka.protocol.EurekaProtocolError;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.EurekaService;
import com.netflix.eureka.service.RegistrationChannel;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.base.BaseMessageBroker;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class AsyncRegistrationHandler implements ConnectionHandler<Object, Object> {

    private final EurekaService eurekaService;

    @Inject
    public AsyncRegistrationHandler(EurekaService eurekaService) {
        this.eurekaService = eurekaService;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        final MessageBroker broker = new BaseMessageBroker(connection);
        final RequestDispatcher dispatcher = new RequestDispatcher(broker);

        PublishSubject<Void> statusObservable = PublishSubject.create();
        broker.incoming().flatMap(new Func1<Object, Observable<Void>>() {
            @Override
            public Observable<Void> call(final Object message) {
                return dispatcher.dispatch(message);
            }
        }).subscribe(statusObservable);

        return statusObservable;
    }

    private class RequestDispatcher {
        private final MessageBroker broker;
        private final AtomicReference<RegistrationChannel> registrationChannelRef = new AtomicReference<RegistrationChannel>();

        private RequestDispatcher(MessageBroker broker) {
            this.broker = broker;
        }

        public Observable<Void> dispatch(final Object message) {
            Observable<Void> response;
            if (message instanceof Register) {
                response = handleRegistration(((Register) message).getInstanceInfo());
            } else {
                if (registrationChannelRef.get() == null) {
                    if (message instanceof Unregister) {
                        return Observable.empty();
                    }
                    return Observable.error(new EurekaProtocolError("Expected registration message prior to " + message));
                }

                if (message instanceof Unregister) {
                    response = handleUnregistration();
                } else if (message instanceof Update) {
                    response = handleUpdate((Update) message);
                } else if (message instanceof Heartbeat) {
                    return handleHeartbeat();
                } else {
                    return Observable.empty();
                }
            }
            return response.doOnCompleted(new Action0() {
                @Override
                public void call() {
                    broker.acknowledge(message);
                }
            });
        }

        private Observable<Void> handleRegistration(InstanceInfo instanceInfo) {
            destroyChannel();
            RegistrationChannel registrationChannel = eurekaService.newRegistrationChannel();
            registrationChannelRef.set(registrationChannel);
            registrationChannel.register(instanceInfo);
            return Observable.empty();
        }

        private Observable<Void> handleHeartbeat() {
            registrationChannelRef.get().heartbeat();
            return Observable.empty();
        }

        private Observable<Void> handleUpdate(Update message) {
            return registrationChannelRef.get().update(message.getInstanceInfo());
        }

        private Observable<Void> handleUnregistration() {
            destroyChannel();
            return Observable.empty();
        }

        private void destroyChannel() {
            RegistrationChannel oldValue = registrationChannelRef.getAndSet(null);
            if (oldValue != null) {
                oldValue.close();
            }
        }
    }
}
