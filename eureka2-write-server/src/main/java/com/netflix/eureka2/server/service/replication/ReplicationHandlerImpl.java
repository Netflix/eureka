package com.netflix.eureka2.server.service.replication;

import com.netflix.eureka2.channel.ChannelFactory;
import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.connection.RetryableConnection;
import com.netflix.eureka2.connection.RetryableConnectionFactory;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.metric.server.WriteServerMetricFactory;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.SenderReplicationChannelFactory;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.utils.rx.RetryStrategyFunc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author David Liu
 */
public class ReplicationHandlerImpl implements ReplicationHandler {

    enum STATE {Idle, Replicating, Closed}

    private static final Logger logger = LoggerFactory.getLogger(ReplicationHandlerImpl.class);

    private static final int DEFAULT_RETRY_WAIT_MILLIS = 500;

    private final ChannelFactory<ReplicationChannel> channelFactory;
    private final int retryWaitMillis;
    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final RetryableConnection<ReplicationChannel> connection;
    private final Subscriber<Void> replicationSubscriber;
    private final AtomicReference<STATE> stateRef;

    public ReplicationHandlerImpl(final InetSocketAddress address,
                                  final EurekaTransports.Codec codec,
                                  final SourcedEurekaRegistry<InstanceInfo> registry,
                                  final InstanceInfo selfInfo,
                                  final WriteServerMetricFactory metricFactory) {
        this(new SenderReplicationChannelFactory(address, codec, metricFactory), DEFAULT_RETRY_WAIT_MILLIS, registry, selfInfo);
    }

    /*visible for testing*/ ReplicationHandlerImpl(
                                  final ChannelFactory<ReplicationChannel> channelFactory,
                                  final int retryWaitMillis,
                                  final SourcedEurekaRegistry<InstanceInfo> registry,
                                  final InstanceInfo selfInfo) {
        this.stateRef = new AtomicReference<>(STATE.Idle);
        this.retryWaitMillis = retryWaitMillis;
        this.registry = registry;
        this.channelFactory = channelFactory;

        final String ownInstanceId = selfInfo.getId();

        final RetryableConnectionFactory<ReplicationChannel> connectionFactory =
                new RetryableConnectionFactory<>(channelFactory);

        connection = connectionFactory.zeroOpConnection(new Func1<ReplicationChannel, Observable<Void>>() {
            @Override
            public Observable<Void> call(final ReplicationChannel replicationChannel) {
                return replicationChannel.hello(new ReplicationHello(ownInstanceId, registry.size()))
                        .take(1)
                        .map(new Func1<ReplicationHelloReply, ReplicationHelloReply>() {
                            @Override
                            public ReplicationHelloReply call(ReplicationHelloReply replicationHelloReply) {
                                if (replicationHelloReply.getSourceId().equals(ownInstanceId)) {
                                    logger.info("{}: Taking out replication connection to itself", ownInstanceId);
                                    replicationChannel.close();  // gracefully close
                                } else {
                                    logger.info("{} received hello back from {}", ownInstanceId, replicationHelloReply.getSourceId());
                                    replicate(replicationChannel);
                                }
                                return replicationHelloReply;
                            }
                        })
                        .ignoreElements()
                        .cast(Void.class);
            }
        });

        this.replicationSubscriber = new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                logger.info("Replication onCompleted");
            }

            @Override
            public void onError(Throwable e) {
                logger.warn("Replication onError", e);
            }

            @Override
            public void onNext(Void aVoid) {
            }
        };
    }

    @Override
    public void startReplication() {
        if (stateRef.compareAndSet(STATE.Idle, STATE.Replicating)) {
            // TODO better retry func?
            connection.getRetryableLifecycle()
                    .retryWhen(new RetryStrategyFunc(retryWaitMillis))
                    .subscribe(new Subscriber<Void>() {
                        @Override
                        public void onCompleted() {
                            logger.info("sender replication connection onCompleted");
                        }

                        @Override
                        public void onError(Throwable e) {
                            logger.warn("sender replication connection onError", e);
                        }

                        @Override
                        public void onNext(Void aVoid) {

                        }
                    });
        }
    }

    @Override
    public void shutdown() {
        STATE prev = stateRef.getAndSet(STATE.Closed);
        if (prev == STATE.Replicating) {
            replicationSubscriber.unsubscribe();
            connection.close();
            channelFactory.shutdown();
        }
    }

    protected void replicate(final ReplicationChannel channel) {
        registry.forInterest(Interests.forFullRegistry(), Source.matcherFor(Source.Origin.LOCAL))
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ChangeNotification<InstanceInfo> notification) {
                        switch (notification.getKind()) {
                            case Add:
                                return channel.register(notification.getData());
                            case Modify:
                                return channel.register(notification.getData());
                            case Delete:
                                return channel.unregister(notification.getData().getId());
                            default:
                                logger.warn("Unrecognised notification kind {}", notification);
                                return Observable.empty();
                        }

                    }
                })
                .subscribe(replicationSubscriber);

    }
}
