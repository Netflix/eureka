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

package com.netflix.eureka.transport;

import io.reactivex.netty.server.RxServer;
import rx.Observable;

/**
 * Represents server side, where for each new client connection we create distinct {@link MessageBroker}.
 * It is equivalent to {@link RxServer}.
 *
 * @author Tomasz Bak
 */
public interface MessageBrokerServer {

    Observable<MessageBroker> clientConnections();

    int getServerPort();

    MessageBrokerServer start();

    void shutdown() throws InterruptedException;
}
