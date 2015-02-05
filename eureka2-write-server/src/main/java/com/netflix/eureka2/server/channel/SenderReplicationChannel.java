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

package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.AbstractClientChannel;
import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.channel.ReplicationChannel.STATE;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class SenderReplicationChannel extends AbstractClientChannel<STATE> implements ReplicationChannel {

    private static final Exception ILLEGAL_STATE_EXCEPTION = new Exception("Operation illegal in current channel state");

    public SenderReplicationChannel(TransportClient client, ReplicationChannelMetrics metrics) {
        super(STATE.Idle, client, metrics);
    }

    @Override
    public Observable<ReplicationHelloReply> hello(final ReplicationHello hello) {
        if (!moveToState(STATE.Idle, STATE.Handshake)) {
            return invalidStateError();
        }
        return connect().switchMap(new Func1<MessageConnection, Observable<ReplicationHelloReply>>() {
            @Override
            public Observable<ReplicationHelloReply> call(final MessageConnection connection) {
                return sendOnConnection(connection, hello)
                        .cast(ReplicationHelloReply.class)
                        .concatWith(
                                connection.incoming().flatMap(new Func1<Object, Observable<ReplicationHelloReply>>() {
                                    @Override
                                    public Observable<ReplicationHelloReply> call(Object o) {
                                        if (o instanceof ReplicationHelloReply) {
                                            moveToState(STATE.Handshake, STATE.Connected);
                                            return Observable.just((ReplicationHelloReply) o);
                                        }
                                        return Observable.error(new Exception("Unexpected message of type " + o.getClass() + " received"));
                                    }
                                })
                        );
            }
        });
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        if (state.get() != STATE.Connected) {
            return invalidStateError();
        }

        return connect().switchMap(new Func1<MessageConnection, Observable<Void>>() {
            @Override
            public Observable<Void> call(MessageConnection connection) {
                return sendOnConnection(connection, new RegisterCopy(instanceInfo));
            }
        });
    }

    @Override
    public Observable<Void> unregister(final String instanceId) {
        if (state.get() != STATE.Connected) {
            return invalidStateError();
        }

        return connect().switchMap(new Func1<MessageConnection, Observable<Void>>() {
            @Override
            public Observable<Void> call(MessageConnection connection) {
                return sendOnConnection(connection, new UnregisterCopy(instanceId));
            }
        });
    }

    @Override
    protected void _close() {
        if (state.get() != STATE.Closed) {
            moveToState(STATE.Closed);
            super._close();
        }
    }

    protected <T> Observable<T> invalidStateError() {
        if (state.get() == STATE.Closed) {
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }
        return Observable.error(ILLEGAL_STATE_EXCEPTION);
    }
}
