package com.netflix.eureka2.server.channel;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.registry.Sourced;
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
public class ReceiverReplicationChannel extends AbstractHandlerChannel<STATES> implements ReplicationChannel, Sourced {

    private static final Logger logger = LoggerFactory.getLogger(ReceiverReplicationChannel.class);

    static final Exception IDLE_STATE_EXCEPTION = new Exception("Replication channel in idle state");
    static final Exception HANDSHAKE_FINISHED_EXCEPTION = new Exception("Handshake already done");
    static final Exception REPLICATION_LOOP_EXCEPTION = new Exception("Self replicating to itself");

    private final SelfInfoResolver selfIdentityService;
    private final ReplicationChannelMetrics metrics;
    private Source replicationSource;

    // A loop is detected by comparing hello message source id with local instance id.
    private boolean replicationLoop;

    public enum STATES {Idle, Opened, Closed}

    private final ConcurrentHashMap<String, InstanceInfo> instanceInfoById = new ConcurrentHashMap<>();

    public ReceiverReplicationChannel(MessageConnection transport,
                                      SelfInfoResolver selfIdentityService,
                                      SourcedEurekaRegistry<InstanceInfo> registry,
                                      final EvictionQueue evictionQueue,
                                      ReplicationChannelMetrics metrics) {
        super(STATES.Idle, transport, registry);
        this.selfIdentityService = selfIdentityService;
        this.metrics = metrics;
        this.metrics.incrementStateCounter(STATES.Idle);

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

    @Override
    public Source getSource() {
        return replicationSource;
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

        replicationSource = Source.replicatedSource(hello.getSourceId() + "_" + UUID.randomUUID().toString());

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

    /**
     * We first eagerly add to the instanceInfoById map, then execute the register operation. Scenarios of this:
     * - register op succeeds. All is good.
     * - register op fails. If no subsequent register/unregister on this instance has arrived on the channel,
     *   we are left with cruft in the local map. This is fine as the channel is lifecycled so the cruft will be
     *   eventually cleaned up. At eviction time, this may cause no-op evictions
     * - register op fails. If a subsequent register arrives, then we are fine as these updates are all serialized
     *   and the holder will deal with the deltas for the notifications.
     * - register op fails. If a subsequent unregister arrives, we will execute unregister as a no-op
     */
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
            logger.debug("Updating an existing instance info with id {}", instanceInfo.getId());
        }

        instanceInfoById.put(instanceInfo.getId(), instanceInfo);
        return registry.register(instanceInfo, replicationSource)
                .map(new Func1<Boolean, Void>() {
                    @Override
                    public Void call(Boolean aBoolean) {
                        // an emit means the tempNewInfo was successfully registered
                        logger.debug("Successfully replicated an {} of {}", (aBoolean ? "add" : "update"), instanceInfo);
                        return null;
                    }
                })
                .ignoreElements();
    }

    /**
     * We first eagerly remove from the instanceInfoById map, then execute the unregister operation. If the unregister
     * fails, we add the instanceInfo back to the map if it's not present. Scenarios of this:
     * - unregister op succeeds. All is good.
     * - unregister op fails. If no subsequent register/unregister on this instance has arrived on the channel, we add
     *   the instance back to the localmap. This element then follows the standard channel lifecycle evicts etc.
     * - unregister op fails. If a subsequent register arrives, then if the register has already updates the map,
     *   then we don't put the unregistered copy back. Otherwise, the register op will override the put back copy.
     * - unregister op fails. If a subsequent unregister arrives, we will execute this new unregister as a no-op
     *   if the item is not added back.
     *
     * Note that in all the failure scenarios above, the current external RegistryReplicator will close the channel
     * and reestablish if the operation fails.
     */
    @Override
    public Observable<Void> unregister(final String instanceId) {
        logger.debug("Removing registration entry for instanceId {}", instanceId);

        if (STATES.Opened != state.get()) {
            return Observable.error(state.get() == STATES.Closed ? CHANNEL_CLOSED_EXCEPTION : IDLE_STATE_EXCEPTION);
        }
        if (replicationLoop) {
            return Observable.error(REPLICATION_LOOP_EXCEPTION);
        }

        final InstanceInfo toUnregister = instanceInfoById.remove(instanceId);
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
                        logger.debug("Successfully replicated an unregister {}", toUnregister);
                        return null;
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        instanceInfoById.putIfAbsent(instanceId, toUnregister);
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
