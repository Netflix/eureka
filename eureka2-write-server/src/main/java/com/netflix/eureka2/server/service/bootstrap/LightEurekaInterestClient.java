package com.netflix.eureka2.server.service.bootstrap;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification.BufferState;
import com.netflix.eureka2.metric.noop.NoOpMessageConnectionMetrics;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.DeleteInstance;
import com.netflix.eureka2.protocol.interest.InterestRegistration;
import com.netflix.eureka2.protocol.common.InterestSetNotification;
import com.netflix.eureka2.protocol.common.StreamStateUpdate;
import com.netflix.eureka2.protocol.interest.UpdateInstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Builder;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.transport.base.BaseMessageConnection;
import com.netflix.eureka2.transport.base.HeartBeatConnection;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.channel.ObservableConnection;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.pipeline.PipelineConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * Light interest client implementation for doing single, one time subscription to full registry
 * content from a remote endpoint. The subscription stream is terminated once buffer end sentinel is received.
 *
 * TODO Consider moving this to a common place, so it could be reused in other scenarios
 * TODO Reuse transport abstraction that now sits in eureka2-client
 * TODO Integrate metrics
 *
 * @author Tomasz Bak
 */
public class LightEurekaInterestClient {

    private static final Logger logger = LoggerFactory.getLogger(LightEurekaInterestClient.class);

    private final Server server;
    private final Scheduler scheduler;
    private final EurekaTransportConfig config;
    private final PipelineConfigurator<Object, Object> pipelineConfigurator;

    public LightEurekaInterestClient(Server server, Scheduler scheduler) {
        this.server = server;
        this.scheduler = scheduler;
        this.config = new BasicEurekaTransportConfig.Builder().build();
        this.pipelineConfigurator = EurekaTransports.interestPipeline(config.getCodec());
    }

    public Observable<ChangeNotification<InstanceInfo>> forInterest(final Interest<InstanceInfo> interest) {
        RxClient<Object, Object> client = RxNetty.createTcpClient(server.getHost(), server.getPort(), pipelineConfigurator);

        return client.connect()
                .map(new Func1<ObservableConnection<Object, Object>, MessageConnection>() {
                    @Override
                    public MessageConnection call(ObservableConnection<Object, Object> connection) {
                        return new HeartBeatConnection(
                                new BaseMessageConnection("bootstrap", connection, NoOpMessageConnectionMetrics.INSTANCE),
                                config.getHeartbeatIntervalMs(), 1, scheduler
                        );
                    }
                }).flatMap(new Func1<MessageConnection, Observable<ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<ChangeNotification<InstanceInfo>> call(final MessageConnection connection) {
                        Observable<Void> submitObservable = connection.submitWithAck(new InterestRegistration(interest));

                        Observable<ChangeNotification<InstanceInfo>> notificationObservable = connection.
                                incoming().
                                takeUntil(new Func1<Object, Boolean>() {
                                    @Override
                                    public Boolean call(Object message) {
                                        if (message instanceof StreamStateUpdate) {
                                            StreamStateUpdate streamStateUpdate = (StreamStateUpdate) message;
                                            return streamStateUpdate.getState() == BufferState.BufferEnd;
                                        }
                                        return false;
                                    }
                                }).
                                flatMap(new Func1<Object, Observable<ChangeNotification<InstanceInfo>>>() {
                                    @Override
                                    public Observable<ChangeNotification<InstanceInfo>> call(Object message) {
                                        Observable ackObservable = connection.acknowledge();

                                        boolean isKnown = message instanceof InterestSetNotification;
                                        if (!isKnown) {
                                            logger.warn("Unrecognized discovery protocol message of type " + message.getClass());
                                            return ackObservable;
                                        }
                                        ChangeNotification<InstanceInfo> notification = toChangeNotification((InterestSetNotification) message);
                                        logger.info("SAW {}", notification);
                                        return notification == null ? ackObservable : ackObservable.concatWith(Observable.just(notification));
                                    }
                                });

                        /**
                         * We need to subscribe eagerly to input stream, prior to submitting interest, so we do not
                         * loose notifications.
                         */
                        Observable result = Observable.merge(notificationObservable, submitObservable)
                                .doOnError(new Action1<Throwable>() {
                                    @Override
                                    public void call(Throwable error) {
                                        logger.error("Bootstrap subscription disconnected with an error", error);
                                    }
                                })
                                .doOnCompleted(new Action0() {
                                    @Override
                                    public void call() {
                                        logger.info("Bootstrap subscription disconnected");
                                    }
                                })
                                .doOnTerminate(new Action0() {
                                    @Override
                                    public void call() {
                                        connection.shutdown();
                                    }
                                });
                        return (Observable<ChangeNotification<InstanceInfo>>) result;
                    }
                });
    }

    private ChangeNotification<InstanceInfo> toChangeNotification(InterestSetNotification message) {
        if (message instanceof AddInstance) {
            return new ChangeNotification<>(Kind.Add, ((AddInstance) message).getInstanceInfo());
        }
        if (message instanceof UpdateInstanceInfo) {
            InstanceInfo instanceInfo = new Builder().withId(((UpdateInstanceInfo) message).getDelta().getId()).build();
            return new ChangeNotification<>(Kind.Modify, instanceInfo);
        }
        if (message instanceof DeleteInstance) {
            InstanceInfo instanceInfo = new Builder().withId(((DeleteInstance) message).getInstanceId()).build();
            return new ChangeNotification<>(Kind.Delete, instanceInfo);
        }
        return null;
    }
}
