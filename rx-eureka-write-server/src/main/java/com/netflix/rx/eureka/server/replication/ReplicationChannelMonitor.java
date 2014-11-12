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

package com.netflix.rx.eureka.server.replication;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.rx.eureka.client.transport.TransportClient;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Scheduler.Worker;
import rx.Subscriber;
import rx.functions.Action0;
import rx.schedulers.Schedulers;

/**
 * {@link ReplicationChannelMonitor} maintains a replication channel with a single write server node.
 * In case of channel failure, the connection is reestablished, with a configurable delay if
 * the disconnect was caused by a channel error.
 *
 * TODO: we should distinguish between transitive and permanent errors.
 *
 * @author Tomasz Bak
 */
public class ReplicationChannelMonitor {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationChannelMonitor.class);

    private enum STATE {Open, Closed}

    /**
     * {@link #targetName} identifies the write server to which replication channel is established.
     * It is used solely for logging purposes.
     */
    private final String targetName;

    private final EurekaServerRegistry<InstanceInfo> eurekaRegistry;
    private final TransportClient transportClient;
    private final long reconnectDelayMs;

    private final AtomicReference<ClientReplicationChannel> replicationChannelRef = new AtomicReference<>();
    private final Worker worker;
    private volatile STATE state;

    public ReplicationChannelMonitor(String targetName, EurekaServerRegistry eurekaRegistry, TransportClient transportClient,
                                     long reconnectDelayMs) {
        this.targetName = targetName;
        this.eurekaRegistry = eurekaRegistry;
        this.transportClient = transportClient;
        this.reconnectDelayMs = reconnectDelayMs;
        this.state = STATE.Open;
        worker = Schedulers.computation().createWorker();
        connect();
    }

    private void connect() {
        logger.info("Setting up replication channel with {}", targetName);

        closeChannel();
        ClientReplicationChannel replicationChannel = new ClientReplicationChannel(
                eurekaRegistry, transportClient);
        replicationChannelRef.set(replicationChannel);

        replicationChannel.asLifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                if (state == STATE.Open) {
                    logger.info("Replication channel {} closed; reconnecting it.", targetName);
                    connect();
                }
            }

            @Override
            public void onError(Throwable e) {
                if (state == STATE.Open) {
                    logger.error("Error in replication channel " + targetName + "; reconnecting in " +
                            reconnectDelayMs + " milliseconds", e);
                    worker.schedule(new Action0() {
                        @Override
                        public void call() {
                            connect();
                        }
                    }, reconnectDelayMs, TimeUnit.MILLISECONDS);
                }
            }

            @Override
            public void onNext(Void aVoid) {
                // Nothing
            }
        });
    }

    public void close() {
        logger.info("Shutting down replication channel to {}", targetName);

        if (state != STATE.Closed) {
            state = STATE.Closed;
            closeChannel();
            worker.unsubscribe();
        }
    }

    protected void closeChannel() {
        ClientReplicationChannel replicationChannel = replicationChannelRef.getAndSet(null);
        if (replicationChannel != null) {
            replicationChannel.close();
        }
    }
}
