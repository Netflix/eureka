package com.netflix.eureka2.server.channel;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.protocol.replication.UpdateCopy;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.Delta;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.ReceiverReplicationChannel.STATES;
import com.netflix.eureka2.server.metric.ReplicationChannelMetrics;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
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

    private final SelfRegistrationService selfRegistrationService;
    private final ReplicationChannelMetrics metrics;
    private Source replicationSource;
    private long currentVersion;

    // A loop is detected by comparing hello message source id with local instance id.
    private boolean replicationLoop;

    public enum STATES {Idle, Opened, Closed}

    private final Map<String, InstanceInfo> instanceInfoById = new HashMap<>();

    public ReceiverReplicationChannel(MessageConnection transport,
                                      SelfRegistrationService selfRegistrationService,
                                      SourcedEurekaRegistry<InstanceInfo> registry,
                                      final EvictionQueue evictionQueue,
                                      ReplicationChannelMetrics metrics) {
        super(STATES.Idle, transport, registry);
        this.selfRegistrationService = selfRegistrationService;
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
        } else if (message instanceof UpdateCopy) {
            InstanceInfo instanceInfo = ((UpdateCopy) message).getInstanceInfo();
            reply = update(instanceInfo);// No need to subscribe, the update() call does the subscription.
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

        return selfRegistrationService.resolve().flatMap(new Func1<InstanceInfo, Observable<ReplicationHelloReply>>() {
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
            logger.info("Overwriting existing registration entry for instance {}", instanceInfo.getId());
        }

        InstanceInfo tempNewInfo = new InstanceInfo.Builder()
                .withInstanceInfo(instanceInfo).withVersion(currentVersion++).build();

        return registry.register(tempNewInfo, replicationSource)
                .ignoreElements()
                .cast(Void.class)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        instanceInfoById.put(instanceInfo.getId(), instanceInfo);
                    }
                });
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        logger.debug("Updating existing registry entry. New info= {}", newInfo);

        if (STATES.Opened != state.get()) {
            return Observable.error(state.get() == STATES.Closed ? CHANNEL_CLOSED_EXCEPTION : IDLE_STATE_EXCEPTION);
        }
        if (replicationLoop) {
            return Observable.error(REPLICATION_LOOP_EXCEPTION);
        }
        InstanceInfo currentInfo = instanceInfoById.get(newInfo.getId());
        if (currentInfo == null) {
            logger.info("Replication update request for non-existing entry {}; handling it as an initial registration", newInfo.getId());
            return register(newInfo);
        }

        InstanceInfo tempNewInfo = new InstanceInfo.Builder()
                .withInstanceInfo(newInfo).withVersion(currentVersion++).build();
        Set<Delta<?>> deltas = tempNewInfo.diffOlder(currentInfo);
        logger.debug("Set of InstanceInfo modified fields: {}", deltas);

        return registry.update(tempNewInfo, deltas, replicationSource)
                .ignoreElements()
                .cast(Void.class)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        instanceInfoById.put(newInfo.getId(), newInfo);
                    }
                });
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
                .ignoreElements()
                .cast(Void.class)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        instanceInfoById.remove(instanceId);
                    }
                });
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
