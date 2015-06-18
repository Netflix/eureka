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

package com.netflix.eureka2.server.transport.tcp.interest;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.channel.InterestChannel;
import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.metric.server.EurekaServerMetricFactory;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import com.netflix.eureka2.server.channel.InterestChannelImpl;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.transport.base.HeartBeatConnection;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class TcpInterestHandler implements ConnectionHandler<Object, Object> {

    private static final Logger logger = LoggerFactory.getLogger(TcpInterestHandler.class);

    /* Visible for testing */ static final int RETRY_INTERVAL_MS = 1000;

    private final EurekaCommonConfig config;
    private final EurekaRegistryView<InstanceInfo> registry;
    private final EurekaServerMetricFactory metricFactory;
    private final Subscription healthStatusSubscription;

    private volatile boolean afterBootstrap;

    @Inject
    public TcpInterestHandler(EurekaCommonConfig config,
                              EurekaRegistryView registry,
                              EurekaHealthStatusAggregator systemHealthStatus,
                              EurekaServerMetricFactory metricFactory) {
        this(config, registry, systemHealthStatus, metricFactory, Schedulers.computation());
    }

    public TcpInterestHandler(EurekaCommonConfig config,
                              EurekaRegistryView registry,
                              EurekaHealthStatusAggregator systemHealthStatus,
                              EurekaServerMetricFactory metricFactory,
                              Scheduler scheduler) {
        this.config = config;
        this.registry = registry;
        this.metricFactory = metricFactory;

        this.healthStatusSubscription = systemHealthStatus.healthStatus()
                .filter(new Func1<HealthStatusUpdate<EurekaHealthStatusAggregator>, Boolean>() {
                    @Override
                    public Boolean call(HealthStatusUpdate<EurekaHealthStatusAggregator> update) {
                        return update.getStatus() == Status.UP;
                    }
                })
                .take(1)
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable error) {
                        logger.error("Healthcheck status stream terminated with an error. Interest connection " +
                                "will not be accepted yet. Re-subscribing shortly", error);
                    }
                })
                .retryWhen(new RetryStrategyFunc(RETRY_INTERVAL_MS, scheduler), scheduler)
                .subscribe(
                        new Subscriber<HealthStatusUpdate<EurekaHealthStatusAggregator>>() {
                            @Override
                            public void onCompleted() {
                                if (!afterBootstrap) {
                                    logger.error("Healthcheck status stream completed, while it is not yet marked as up.");
                                }
                            }

                            @Override
                            public void onError(Throwable e) {
                                // This should never happen
                                logger.error("Healthcheck stream terminated with an error and went out of retry loop", e);
                            }

                            @Override
                            public void onNext(HealthStatusUpdate<EurekaHealthStatusAggregator> update) {
                                afterBootstrap = true;
                                logger.info("Enabling TCP interest port for service");
                            }
                        }
                );
    }

    @PreDestroy
    public void shutdown() {
        healthStatusSubscription.unsubscribe();
    }

    @Override
    public Observable<Void> handle(ObservableConnection<Object, Object> connection) {
        if (logger.isDebugEnabled()) {
            logger.debug("New TCP discovery client connection");
        }
        if (!afterBootstrap) {
            logger.info("Server bootstrap not finished; discarding client connection");
            return Observable.error(new IllegalStateException("Server bootstrap not finished; discarding client connection"));
        }

        MessageConnection broker = new HeartBeatConnection(
                new BaseMessageConnection(Names.INTEREST, connection, metricFactory.getDiscoveryConnectionMetrics()),
                config.getHeartbeatIntervalMs(), 3,
                Schedulers.computation()
        );

        InterestChannel interestChannel =
                new InterestChannelImpl(registry, broker, metricFactory.getInterestChannelMetrics());

        // Since this is a discovery handler which only handles interest subscriptions,
        // the channel is created on connection accept. We subscribe here for the sake
        // of logging only.
        Observable<Void> lifecycleObservable = interestChannel.asLifecycleObservable();
        lifecycleObservable.subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Connection from client terminated");
            }

            @Override
            public void onError(Throwable e) {
                logger.info("Connection from client terminated with error");
                logger.debug("Connection exception", e);
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });
        return lifecycleObservable;
    }
}
