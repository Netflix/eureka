package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.ChannelFunctions;
import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.protocol.common.InterestSetNotification;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import com.netflix.eureka2.utils.rx.RxFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author David Liu
 */
public class ReceiverReplicationChannel extends AbstractHandlerChannel<ReplicationChannel.STATE> implements ReplicationChannel {

    private static final Logger logger = LoggerFactory.getLogger(ReceiverReplicationChannel.class);

    static final Exception IDLE_STATE_EXCEPTION = new Exception("Replication channel in idle state");
    static final Exception HANDSHAKE_FINISHED_EXCEPTION = new Exception("Handshake already done");

    private final ChannelFunctions channelFunctions = new ChannelFunctions(logger);

    private final Observable<Void> control;
    private final Observable<ChangeNotification<InstanceInfo>> data;

    private final SelfInfoResolver selfIdentityService;
    private final Subscriber<Void> controlSubscriber;
    private final Subscriber<Void> channelSubscriber;
    private final ConcurrentHashMap<String, InstanceInfo> idsVsInstance = new ConcurrentHashMap<>();

    private volatile Source replicationSource;

    public ReceiverReplicationChannel(MessageConnection transport,
                                    SelfInfoResolver selfIdentityService,
                                    final EurekaRegistry<InstanceInfo> registry,
                                    ReplicationChannelMetrics metrics) {
        super(STATE.Idle, transport, metrics);
        this.selfIdentityService = selfIdentityService;
        this.channelSubscriber = new LoggingSubscriber<>(logger, "channel");
        this.controlSubscriber = new LoggingSubscriber<>(logger, "control");

        Observable<Object> input = connectInputToLifecycle(transport.incoming()).replay(1).refCount();

        data = input
                .filter(new Func1<Object, Boolean>() {
                    @Override
                    public Boolean call(Object msg) {
                        return msg instanceof InterestSetNotification;
                    }
                })
                .cast(InterestSetNotification.class)
                // TODO: is this concat map check for channel state necessary anymore?
                .concatMap(new Func1<InterestSetNotification, Observable<InterestSetNotification>>() {
                    @Override
                    public Observable<InterestSetNotification> call(InterestSetNotification interestSetNotification) {
                        if (STATE.Connected != state.get()) {
                            return Observable.error(state.get() == STATE.Closed ? CHANNEL_CLOSED_EXCEPTION : IDLE_STATE_EXCEPTION);
                        }
                        return Observable.just(interestSetNotification);
                    }
                })
                .map(new Func1<InterestSetNotification, ChangeNotification<InstanceInfo>>() {
                    @Override
                    public ChangeNotification<InstanceInfo> call(InterestSetNotification message) {
                        return channelFunctions.channelMessageToNotification(message, replicationSource, idsVsInstance);
                    }
                })
                .filter(RxFunctions.filterNullValuesFunc());

        control = input
                .filter(new Func1<Object, Boolean>() {
                    @Override
                    public Boolean call(Object msg) {
                        return msg instanceof ReplicationHello;
                    }
                })
                .take(1)
                .cast(ReplicationHello.class)
                .flatMap(new Func1<ReplicationHello, Observable<Source>>() {
                    @Override
                    public Observable<Source> call(ReplicationHello replicationHello) {
                        return processHello(replicationHello);
                    }
                })
                .doOnNext(new Action1<Source>() {
                    @Override
                    public void call(Source replicationSource) {
                        if (replicationSource == null) {
                            logger.info("Shutting down the channel as it is replicating to itself");
                            close();  // close this channel
                        } else {  // else setup eviction of prev channel's data
                            Observable<Void> evictionOb = channelFunctions.setUpPrevChannelEviction(replicationSource, registry);
                            evictionOb.subscribe(new LoggingSubscriber<Void>(logger, "eviction"));
                            registry.connect(replicationSource, data).subscribe(channelSubscriber);
                        }
                    }
                })
                .ignoreElements()
                .cast(Void.class);

        logger.info("created new {}", this.getClass().getSimpleName());

        start();
    }

    public void start() {
        control.subscribe(controlSubscriber);
    }

    @Override
    public Source getSource() {
        return replicationSource;
    }

    @Override
    protected void _close() {
        STATE from = moveToState(STATE.Closed);
        if (from != STATE.Closed) {  // if this is the first/only close
            controlSubscriber.unsubscribe();
            channelSubscriber.unsubscribe();
            idsVsInstance.clear();
            super._close();
        }
    }

    /**
     * @return the replicationSource if this is not a replication loop, null otherwise
     */
    private Observable<Source> processHello(final ReplicationHello hello) {
        logger.debug("Replication hello message: {}", hello);

        if(!moveToState(STATE.Idle, STATE.Handshake)) {
            return Observable.error(state.get() == STATE.Closed ? CHANNEL_CLOSED_EXCEPTION : HANDSHAKE_FINISHED_EXCEPTION);
        }

        replicationSource = hello.getSource();

        return selfIdentityService.resolve().take(1).flatMap(new Func1<InstanceInfo, Observable<Source>>() {
            @Override
            public Observable<Source> call(InstanceInfo instanceInfo) {
                boolean replicationLoop = instanceInfo.getId().equals(hello.getSource().getName());

                Source replySource = new Source(Source.Origin.REPLICATED, instanceInfo.getId(), hello.getSource().getId());
                ReplicationHelloReply reply = new ReplicationHelloReply(replySource, false);
                sendOnTransport(reply);
                moveToState(STATE.Handshake, STATE.Connected);

                return replicationLoop ? Observable.<Source>just(null) : Observable.just(replicationSource);
            }
        });
    }


    // FIXME here be obsolete apis, remove
    @Override
    public Observable<Void> replicate(ChangeNotification<InstanceInfo> notification) {
        return null;
    }

    @Override
    public Observable<ReplicationHelloReply> hello(final ReplicationHello hello) {
        return Observable.empty();
    }
}
