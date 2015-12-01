/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.eureka2.ext.grpc.transport.server;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.ChannelPipelineFactory;
import com.netflix.eureka2.spi.channel.InterestHandler;
import com.netflix.eureka2.spi.channel.RegistrationHandler;
import com.netflix.eureka2.spi.channel.ReplicationHandler;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import rx.Observable;
import rx.Subscription;
import rx.subscriptions.BooleanSubscription;

/**
 */
public class GrpcEurekaServerTransportFactory implements EurekaServerTransportFactory {

    @Override
    public Observable<ServerContext> connect(int port,
                                             Source serverSource,
                                             ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory,
                                             ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory,
                                             ChannelPipelineFactory<Object, Object> replicationAcceptor) {
        return Observable.create(subscriber -> {
            GrpcEurekaServer server;
            try {
                server = new GrpcEurekaServer(port, serverSource, registrationPipelineFactory, interestPipelineFactory, replicationAcceptor);
            } catch (Exception e) {
                subscriber.onError(e);
                return;
            }

            Subscription s = BooleanSubscription.create(() -> {
                server.shutdown();
            });
            subscriber.add(s);

            subscriber.onNext(new GrpcServerContext(server.getServerPort()));
        });
    }

    private static class GrpcServerContext implements ServerContext {
        private final int port;

        private GrpcServerContext(int port) {
            this.port = port;
        }

        @Override
        public int getPort() {
            return port;
        }
    }
}
