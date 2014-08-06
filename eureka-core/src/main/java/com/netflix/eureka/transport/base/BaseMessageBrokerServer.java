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

package com.netflix.eureka.transport.base;

import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.MessageBrokerServer;
import io.reactivex.netty.server.RxServer;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class BaseMessageBrokerServer<I, O> implements MessageBrokerServer<I, O> {
    // Package private for testing purposes.
    final RxServer<I, O> server;
    private final PublishSubject<MessageBroker<I, O>> brokersSubject;

    public BaseMessageBrokerServer(RxServer<I, O> server, PublishSubject<MessageBroker<I, O>> brokersSubject) {
        this.server = server;
        this.brokersSubject = brokersSubject;
    }

    @Override
    public BaseMessageBrokerServer<I, O> start() {
        server.start();
        return this;
    }

    @Override
    public Observable<MessageBroker<I, O>> clientConnections() {
        return brokersSubject;
    }

    @Override
    public int getServerPort() {
        return server.getServerPort();
    }

    @Override
    public void shutdown() throws InterruptedException {
        server.shutdown();
    }
}
