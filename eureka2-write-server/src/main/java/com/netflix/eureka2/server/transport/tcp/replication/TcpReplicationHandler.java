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

package com.netflix.eureka2.server.transport.tcp.replication;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel;
import com.netflix.eureka2.server.config.WriteServerConfig;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.transport.base.HeartBeatConnection;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.functions.Func2;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

import javax.inject.Inject;

/**
 * @author Tomasz Bak
 */
public class TcpReplicationHandler implements ConnectionHandler<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(TcpReplicationHandler.class);

    private final WriteServerConfig config;
    private final SelfInfoResolver selfIdentityService;
    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final WriteServerMetricFactory metricFactory;

    private final BehaviorSubject<ReceiverReplicationChannel> channelSubject;

    @Inject
    public TcpReplicationHandler(WriteServerConfig config,
                                 SelfInfoResolver selfIdentityService,
                                 SourcedEurekaRegistry registry,
                                 WriteServerMetricFactory metricFactory) {
        this.config = config;
        this.selfIdentityService = selfIdentityService;
        this.registry = registry;
        this.metricFactory = metricFactory;
        this.channelSubject = BehaviorSubject.create();

        channelSubject
                .scan(new Func2<ReceiverReplicationChannel, ReceiverReplicationChannel, ReceiverReplicationChannel>() {
                    @Override
                    public ReceiverReplicationChannel call(ReceiverReplicationChannel prev, ReceiverReplicationChannel curr) {
                        setUpEviction(prev, curr).subscribe(new Subscriber<Long>() {
                            @Override
                            public void onCompleted() {

                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onNext(Long aLong) {
                                logger.info("Evicted {} instances in one round of eviction due to a new receiverReplicationChannel creation", aLong);
                            }
                        });

                        return curr;
                    }
                })
                .subscribe(new NoOpSubscriber<ReceiverReplicationChannel>());
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        final MessageConnection broker = new HeartBeatConnection(
                new BaseMessageConnection("replicationReceiver", connection, metricFactory.getReplicationReceiverConnectionMetrics()),
                config.getHeartbeatIntervalMs(), 3,
                Schedulers.computation()
        );

        return doHandle(broker).asLifecycleObservable();
    }

    /* visible for testing */ ReceiverReplicationChannel doHandle(MessageConnection connection) {
        ReceiverReplicationChannel channel =
                new ReceiverReplicationChannel(connection, selfIdentityService, registry, metricFactory.getReplicationChannelMetrics());

        channelSubject.onNext(channel);

        return channel;
    }

    private Observable<Long> setUpEviction(final ReceiverReplicationChannel prev, final ReceiverReplicationChannel curr) {
        // once a new channel is available, wait for the first bufferEnd to be emitted. Once it is emitted,
        // return a reference to the previous channel.

        return curr.getStreamStateNotifications()
                .filter(new Func1<ChangeNotification<InstanceInfo>, Boolean>() {
                    @Override
                    public Boolean call(ChangeNotification<InstanceInfo> notification) {
                        if (notification instanceof StreamStateNotification) {
                            StreamStateNotification<InstanceInfo> n = (StreamStateNotification<InstanceInfo>) notification;
                            if (n.getBufferState() == StreamStateNotification.BufferState.BufferEnd) {
                                return true;
                            }
                        }
                        return false;
                    }
                })
                .take(1)
                .map(new Func1<ChangeNotification<InstanceInfo>, ReceiverReplicationChannel>() {
                    @Override
                    public ReceiverReplicationChannel call(ChangeNotification<InstanceInfo> notification) {
                        return prev;
                    }
                })
                .flatMap(new Func1<ReceiverReplicationChannel, Observable<Long>>() {
                    @Override
                    public Observable<Long> call(final ReceiverReplicationChannel channel) {
                        return channel.asLifecycleObservable()
                                .materialize()
                                .flatMap(new Func1<Notification<Void>, Observable<Long>>() {
                                    @Override
                                    public Observable<Long> call(Notification<Void> rxNotification) {
                                        // wait for the old channel to be closed before starting the eviction
                                        // since the input is a void observable OnError or OnCompleted are both fine
                                        Source toEvict = channel.getSource();
                                        if (toEvict != null) {
                                            return registry.evictAll(Source.matcherFor(toEvict));
                                        } else {
                                            return Observable.empty();
                                        }
                                    }
                                });
                    }
                });
    }

}
