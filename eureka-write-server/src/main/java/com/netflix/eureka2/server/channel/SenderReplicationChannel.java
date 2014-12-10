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
import com.netflix.eureka2.transport.TransportClient;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.protocol.replication.UpdateCopy;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.channel.SenderReplicationChannel.STATE;
import com.netflix.eureka2.transport.MessageConnection;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class SenderReplicationChannel extends AbstractClientChannel<STATE> implements ReplicationChannel {

    private static final Exception HANDSHAKE_FINISHED_EXCEPTION = new Exception("Handshake already done");

    public enum STATE {Idle, Handshake, Connected, Closed}

    public SenderReplicationChannel(TransportClient client) {
        super(STATE.Idle, client);
    }

    @Override
    public Observable<ReplicationHelloReply> hello(final ReplicationHello hello) {
        if (state.get() == STATE.Closed) {
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }
        if (!state.compareAndSet(STATE.Idle, STATE.Handshake)) {
            return Observable.error(HANDSHAKE_FINISHED_EXCEPTION);
        }
        return connect().switchMap(new Func1<MessageConnection, Observable<ReplicationHelloReply>>() {
            @Override
            public Observable<ReplicationHelloReply> call(final MessageConnection connection) {
                return connection.submit(hello).flatMap(new Func1<Void, Observable<ReplicationHelloReply>>() {
                    @Override
                    public Observable<ReplicationHelloReply> call(Void aVoid) {
                        return connection.incoming().flatMap(new Func1<Object, Observable<ReplicationHelloReply>>() {
                            @Override
                            public Observable<ReplicationHelloReply> call(Object o) {
                                if (o instanceof ReplicationHelloReply) {
                                    return Observable.just((ReplicationHelloReply) o);
                                }
                                return Observable.error(new Exception("Unexpected message of type " + o.getClass() + " received"));
                            }
                        });
                    }
                });
            }
        });
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        if (state.get() == STATE.Closed) {
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }

        return connect().switchMap(new Func1<MessageConnection, Observable<Void>>() {
            @Override
            public Observable<Void> call(MessageConnection connection) {
                return connection.submit(new RegisterCopy(instanceInfo));
            }
        });
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        if (state.get() == STATE.Closed) {
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }

        return connect().switchMap(new Func1<MessageConnection, Observable<Void>>() {
            @Override
            public Observable<Void> call(MessageConnection connection) {
                return connection.submit(new UpdateCopy(newInfo));
            }
        });
    }

    @Override
    public Observable<Void> unregister(final String instanceId) {
        if (state.get() == STATE.Closed) {
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }

        return connect().switchMap(new Func1<MessageConnection, Observable<Void>>() {
            @Override
            public Observable<Void> call(MessageConnection connection) {
                return connection.submit(new UnregisterCopy(instanceId));
            }
        });
    }
}
