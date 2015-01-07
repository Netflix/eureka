package com.netflix.eureka2.server.channel;

import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel.STATES;
import com.netflix.eureka2.server.metric.ReplicationChannelMetrics;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 *
 * @author Nitesh Kant
 */
public class ReceiverReplicationChannel extends AbstractHandlerChannel<STATES> implements ReplicationChannel {

    private static final Logger logger = LoggerFactory.getLogger(ReceiverReplicationChannel.class);

    static final Exception IDLE_STATE_EXCEPTION = new Exception("Replication channel in idle state");
    static final Exception HANDSHAKE_FINISHED_EXCEPTION = new Exception("Handshake already done");
    static final Exception REPLICATION_LOOP_EXCEPTION = new Exception("Self replicating to itself");

    private final SelfInfoResolver selfIdentityService;
    private final ReplicationChannelMetrics metrics;
    private Source replicationSource;
    private long currentVersion;

    // A loop is detected by comparing hello message source id with local instance id.
    private boolean replicationLoop;

    public enum STATES {Idle, Opened, Closed}

    private final Map<String, InstanceInfo> instanceInfoById = new HashMap<>();

    public ReceiverReplicationChannel(MessageConnection transport,
                                      SelfInfoResolver selfIdentityService,
                                      SourcedEurekaRegistry<InstanceInfo> registry,
                                      final EvictionQueue evictionQueue,
                                      ReplicationChannelMetrics metrics) {
        super(STATES.Idle, transport, registry);
        this.selfIdentityService = selfIdentityService;
        this.metrics = metrics;
        this.metrics.incrementStateCounter(STATES.Idle);

        currentVersion = System.currentTimeMillis();

        subscribeToTransportInput(new Action1<Object>() {
            @Override
            public void call(Object message) {
                dispatchMessageFromClient(message);
            }
        });

        transport.lifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                evict();
            }

            @Override
            public void onError(Throwable e) {
                evict();
            }

            @Override
            public void onNext(Void aVoid) {
                // No op
            }

            private void evict() {
                if (!replicationLoop) {
                    logger.info("Replication channel disconnected; putting all registrations from the channel in the eviction queue");
                    for (InstanceInfo instanceInfo : instanceInfoById.values()) {
                        logger.info("Replication channel disconnected; adding instance {} to the eviction queue", instanceInfo.getId());
                        evictionQueue.add(instanceInfo, replicationSource);
                    }
                }
            }
        });
    }

    protected void dispatchMessageFromClient(final Object message) {
        Observable<?> reply;
        if (message instanceof ReplicationHello) {
            reply = hello((ReplicationHello) message);
        } else if (message instanceof RegisterCopy) {
            InstanceInfo instanceInfo = ((RegisterCopy) message).getInstanceInfo();
            reply = register(instanceInfo);// No need to subscribe, the register() call does the subscription.
        } else if (message instanceof UnregisterCopy) {
            reply = unregister(((UnregisterCopy) message).getInstanceId());// No need to subscribe, the unregister() call does the subscription.
        } else {
            reply = Observable.error(new EurekaProtocolError("Unexpected message " + message));
        }
        reply.ignoreElements().cast(Void.class).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                // No-op
            }

            @Override
            public void onError(Throwable e) {
                sendErrorOnTransport(e);
            }

            @Override
            public void onNext(Void aVoid) {
                // No-op
            }
        });
    }

    @Override
    public Observable<ReplicationHelloReply> hello(final ReplicationHello hello) {
        logger.debug("Replication hello message: {}", hello);

        if (!state.compareAndSet(STATES.Idle, STATES.Opened)) {
            return Observable.error(state.get() == STATES.Closed ? CHANNEL_CLOSED_EXCEPTION : HANDSHAKE_FINISHED_EXCEPTION);
        }
        metrics.stateTransition(STATES.Idle, STATES.Opened);

        replicationSource = Source.replicationSource(hello.getSourceId());

        return selfIdentityService.resolve().flatMap(new Func1<InstanceInfo, Observable<ReplicationHelloReply>>() {
            @Override
            public Observable<ReplicationHelloReply> call(InstanceInfo instanceInfo) {
                replicationLoop = instanceInfo.getId().equals(hello.getSourceId());
                ReplicationHelloReply reply = new ReplicationHelloReply(instanceInfo.getId(), false);
                sendOnTransport(reply);
                return Observable.just(reply);
            }
        });
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        logger.debug("Replicated registry entry: {}", instanceInfo);

        if (STATES.Opened != state.get()) {
            return Observable.error(state.get() == STATES.Closed ? CHANNEL_CLOSED_EXCEPTION : IDLE_STATE_EXCEPTION);
        }
        if (replicationLoop) {
            return Observable.error(REPLICATION_LOOP_EXCEPTION);
        }

        if (instanceInfoById.containsKey(instanceInfo.getId())) {
            logger.info("Updating an existing instance info with id {}", instanceInfo.getId());
        }

        final InstanceInfo tempNewInfo = new InstanceInfo.Builder()
                .withInstanceInfo(instanceInfo).withVersion(currentVersion++).build();

        return registry.register(tempNewInfo, replicationSource)
                .map(new Func1<Boolean, Void>() {
                    @Override
                    public Void call(Boolean aBoolean) {
                        // an emit means the tempNewInfo was successfully registered
                        instanceInfoById.put(tempNewInfo.getId(), tempNewInfo);
                        logger.debug("Successfully replicated an {}", (aBoolean ? "add" : "update"));
                        return null;
                    }
                })
                .ignoreElements();
    }

    @Override
    public Observable<Void> unregister(final String instanceId) {
        logger.debug("Removing registration entry for instanceId {}", instanceId);

        if (STATES.Opened != state.get()) {
            return Observable.error(state.get() == STATES.Closed ? CHANNEL_CLOSED_EXCEPTION : IDLE_STATE_EXCEPTION);
        }
        if (replicationLoop) {
            return Observable.error(REPLICATION_LOOP_EXCEPTION);
        }

        InstanceInfo toUnregister = instanceInfoById.get(instanceId);
        if (toUnregister == null) {
            logger.info("Replicated unregister request for unknown instance {}", instanceId);
            return Observable.empty();
        }

        return registry.unregister(toUnregister, replicationSource)
                .map(new Func1<Boolean, Void>() {
                    @Override
                    public Void call(Boolean aBoolean) {
                        // an emit means the tempNewInfo was successfully unregistered
                        instanceInfoById.remove(instanceId);
                        logger.debug("Successfully replicated an unregister");
                        return null;
                    }
                })
                .ignoreElements();
    }

    @Override
    protected void _close() {
        if (state.getAndSet(STATES.Closed) == STATES.Closed) {
            return;
        }
        metrics.stateTransition(STATES.Opened, STATES.Closed);
        super._close();
        unregisterAll();
    }

    /**
     * On channel close we have to unregister all registry entries provided by this channel.
     */
    private void unregisterAll() {
        for (String id : instanceInfoById.keySet()) {
            unregister(id);
        }
    }
}
