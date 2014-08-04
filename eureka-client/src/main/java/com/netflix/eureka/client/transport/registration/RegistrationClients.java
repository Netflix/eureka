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

package com.netflix.eureka.client.transport.registration;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka.transport.EurekaTransports;
import com.netflix.eureka.client.transport.registration.protocol.asynchronous.AsyncRegistrationClient;
import com.netflix.eureka.client.transport.registration.protocol.http.HttpRegistrationClient;
import com.netflix.eureka.transport.MessageBroker;
import io.netty.buffer.ByteBuf;
import io.netty.handler.logging.LogLevel;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClient;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class RegistrationClients {

    // FIXME Shall we get complete URL (including host/port) from configuration?
    static final String BASE_URI = "/discovery";

    public static Observable<RegistrationClient> tcpRegistrationClient(String host, int port) {
        Observable<MessageBroker> messageBrokerObservable = EurekaTransports.tcpRegistrationClient(host, port);
        return messageBrokerObservable.map(new Func1<MessageBroker, RegistrationClient>() {
            @Override
            public RegistrationClient call(MessageBroker messageBroker) {
                return new AsyncRegistrationClient(messageBroker, 0, TimeUnit.MILLISECONDS);
            }
        });
    }

    public static RegistrationClient websocketRegistrationClient(String host, int port) {
        throw new RuntimeException("not implemented");
    }

    public static RegistrationClient httpRegistrationClient(String host, int port) {
        HttpClient<ByteBuf, ByteBuf> httpClient = RxNetty.<ByteBuf, ByteBuf>newHttpClientBuilder(host, port)
                .enableWireLogging(LogLevel.ERROR)
                .build();
        return new HttpRegistrationClient(BASE_URI, httpClient);
    }
}
