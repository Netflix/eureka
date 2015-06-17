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
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.DeleteInstance;
import com.netflix.eureka2.protocol.common.StreamStateUpdate;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.TransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class SenderReplicationChannel extends AbstractClientChannel<STATE> implements ReplicationChannel {

    private static final Logger logger = LoggerFactory.getLogger(SenderReplicationChannel.class);

    private static final Exception ILLEGAL_STATE_EXCEPTION = new Exception("Operation illegal in current channel state");

    private Source selfSource;

    public SenderReplicationChannel(TransportClient client, ReplicationChannelMetrics metrics) {
        super(STATE.Idle, client, metrics);
    }

    @Override
    public Observable<ReplicationHelloReply> hello(final ReplicationHello hello) {
        selfSource = hello.getSource();

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
    public Observable<Void> replicate(final ChangeNotification<InstanceInfo> notification) {
        return connect().switchMap(new Func1<MessageConnection, Observable<Void>>() {
            @Override
            public Observable<Void> call(MessageConnection connection) {
                if (state.get() != STATE.Connected) {
                    return invalidStateError();
                }

                switch (notification.getKind()) {
                    case Add:
                    case Modify:
                        return sendOnConnection(connection, new AddInstance(notification.getData()));
                    case Delete:
                        return sendOnConnection(connection, new DeleteInstance(notification.getData().getId()));
                    case BufferSentinel:
                        return sendOnConnection(connection, new StreamStateUpdate((StreamStateNotification<InstanceInfo>)notification));
                    default:
                        logger.warn("Unrecognised notification kind {}, not sending", notification);
                        return Observable.empty();
                }
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

    @Override
    public Source getSource() {
        return selfSource;
    }
}
