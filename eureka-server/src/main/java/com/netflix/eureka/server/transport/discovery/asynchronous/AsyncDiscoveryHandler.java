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

package com.netflix.eureka.server.transport.discovery.asynchronous;

import java.util.concurrent.atomic.AtomicReference;

import com.google.inject.Inject;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.ModifyNotification;
import com.netflix.eureka.protocol.EurekaProtocolError;
import com.netflix.eureka.protocol.discovery.AddInstance;
import com.netflix.eureka.protocol.discovery.DeleteInstance;
import com.netflix.eureka.protocol.discovery.RegisterInterestSet;
import com.netflix.eureka.protocol.discovery.UnregisterInterestSet;
import com.netflix.eureka.protocol.discovery.UpdateInstanceInfo;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.service.EurekaServerService;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.base.BaseMessageBroker;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

/**
 * @author Tomasz Bak
 */
public class AsyncDiscoveryHandler implements ConnectionHandler<Object, Object> {

    private final EurekaServerService eurekaService;

    @Inject
    public AsyncDiscoveryHandler(EurekaServerService eurekaService) {
        this.eurekaService = eurekaService;
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        final MessageBroker broker = new BaseMessageBroker(connection);
        final RequestDispatcher dispatcher = new RequestDispatcher(broker);

        final Observable<Void> observable = broker.incoming().flatMap(new Func1<Object, Observable<Void>>() {
            @Override
            public Observable<Void> call(final Object message) {
                return dispatcher.dispatch(message);
            }
        }).doOnTerminate(new Action0() {
            @Override
            public void call() {
                dispatcher.shutdownActiveChannel();
            }
        });

        return Observable.create(new OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                observable.subscribe(subscriber);
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        dispatcher.shutdownActiveChannel();
                    }
                }));
            }
        });
    }

    private class RequestDispatcher {
        private final MessageBroker broker;
        private final AtomicReference<InterestChannel> interestChannelRef = new AtomicReference<InterestChannel>();

        private RequestDispatcher(MessageBroker broker) {
            this.broker = broker;
        }

        public Observable<Void> dispatch(final Object message) {
            Observable<Void> response;
            if (message instanceof RegisterInterestSet) {
                response = handleInterestSetRegistration((RegisterInterestSet) message);
            } else if (message instanceof UnregisterInterestSet) {
                response = handleInterestSetUnregistration();
            } else {
                return Observable.error(new EurekaProtocolError("Unexpected message " + message));
            }

            return response.doOnCompleted(new Action0() {
                @Override
                public void call() {
                    broker.acknowledge(message);
                }
            });
        }

        private Observable<Void> handleInterestSetRegistration(RegisterInterestSet message) {
            shutdownActiveChannel();
            // TODO: we do not need set, just one element
            final InterestChannel interestChannel = eurekaService.forInterest(message.toComposite());
            interestChannelRef.set(interestChannel);

            return interestChannel.asObservable().flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<Void>>() {
                @Override
                public Observable<Void> call(ChangeNotification<InstanceInfo> notification) {
                    switch (notification.getKind()) {
                        case Add:
                            return broker.submit(new AddInstance(notification.getData()));
                        case Delete:
                            return broker.submit(new DeleteInstance(notification.getData().getId()));
                        case Modify:
                            Observable<Void> last = null;
                            ModifyNotification<InstanceInfo> modifyNotification = (ModifyNotification<InstanceInfo>) notification;
                            for (Delta<?> delta : modifyNotification.getDelta()) {
                                last = broker.submit(new UpdateInstanceInfo(delta));
                            }
                            return last;
                    }
                    return null;
                }
            }).doOnTerminate(new Action0() {
                @Override
                public void call() {
                    interestChannelRef.compareAndSet(interestChannel, null);
                }
            });
        }

        private Observable<Void> handleInterestSetUnregistration() {
            shutdownActiveChannel();
            return Observable.empty();
        }

        private void shutdownActiveChannel() {
            InterestChannel activeChannel = interestChannelRef.getAndSet(null);
            if (activeChannel != null) {
                activeChannel.close();
            }
        }
    }
}
