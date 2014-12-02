package com.netflix.eureka2.server.service;

import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.replication.RegisterCopy;
import com.netflix.eureka2.protocol.replication.UnregisterCopy;
import com.netflix.eureka2.protocol.replication.UpdateCopy;
import com.netflix.eureka2.registry.Delta;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.registry.EurekaServerRegistry;
import com.netflix.eureka2.server.registry.EurekaServerRegistry.Status;
import com.netflix.eureka2.server.registry.eviction.EvictionQueue;
import com.netflix.eureka2.server.registry.Source;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 *
 * @author Nitesh Kant
 */
public class RecvReplicationChannel extends AbstractChannel<RecvReplicationChannel.STATES> implements ReplicationChannel {

    private static final Logger logger = LoggerFactory.getLogger(RecvReplicationChannel.class);
    private final Source replicationSource;
    private final ReplicationChannelMetrics metrics;
    private long currentVersion;

    protected enum STATES {Opened, Closed}

    private final Map<String, InstanceInfo> instanceInfoById = new HashMap<>();

    public RecvReplicationChannel(MessageConnection transport,
                                  EurekaServerRegistry<InstanceInfo> registry,
                                  final EvictionQueue evictionQueue,
                                  ReplicationChannelMetrics metrics) {
        super(STATES.Opened, transport, registry);
        this.replicationSource = Source.replicationSource(UUID.randomUUID().toString());  // FIXME use the sent over replication source id
        this.metrics = metrics;
        this.metrics.incrementStateCounter(STATES.Opened);

        currentVersion = System.currentTimeMillis();

        subscribeToTransportInput(new Action1<Object>() {
            @Override
            public void call(Object message) {
                if (message instanceof RegisterCopy) {
                    InstanceInfo instanceInfo = ((RegisterCopy) message).getInstanceInfo();
                    register(instanceInfo);// No need to subscribe, the register() call does the subscription.
                } else if (message instanceof UnregisterCopy) {
                    unregister(((UnregisterCopy) message).getInstanceId());// No need to subscribe, the unregister() call does the subscription.
                } else if (message instanceof UpdateCopy) {
                    InstanceInfo instanceInfo = ((UpdateCopy) message).getInstanceInfo();
                    update(instanceInfo);// No need to subscribe, the update() call does the subscription.
                } else {
                    sendErrorOnTransport(new EurekaProtocolError("Unexpected message " + message));
                }
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
                logger.info("Replication channel disconnected; putting all registrations from the channel in the eviction queue");
                for (InstanceInfo instanceInfo : instanceInfoById.values()) {
                    logger.info("Replication channel disconnected; adding instance {} to the eviction queue", instanceInfo.getId());
                    evictionQueue.add(instanceInfo, replicationSource);
                }
            }
        });
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        logger.debug("Replicated registry entry: {}", instanceInfo);

        if (STATES.Closed == state.get()) {
            /**
             * Since channel is already closed and hence the transport, we don't need to send an error on transport.
             */
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }
        if (instanceInfoById.containsKey(instanceInfo.getId())) {
            logger.info("Overwriting existing registration entry for instance {}", instanceInfo.getId());
        }

        InstanceInfo tempNewInfo = new InstanceInfo.Builder()
                .withInstanceInfo(instanceInfo).withVersion(currentVersion++).build();

        Observable<Status> registerResult = registry.register(tempNewInfo, replicationSource);
        registerResult.subscribe(new Subscriber<Status>() {
            @Override
            public void onCompleted() {
                instanceInfoById.put(instanceInfo.getId(), instanceInfo);
                sendAckOnTransport();
            }

            @Override
            public void onError(Throwable e) {
                sendErrorOnTransport(e);
            }

            @Override
            public void onNext(Status status) {
                // No op
            }
        }); // Callers aren't required to subscribe, so it is eagerly subscribed.
        return registerResult.ignoreElements().cast(Void.class);
    }

    @Override
    public Observable<Void> update(final InstanceInfo newInfo) {
        logger.debug("Updating existing registry entry. New info= {}", newInfo);

        if (STATES.Closed == state.get()) {
            /**
             * Since channel is already closed and hence the transport, we don't need to send an error on transport.
             */
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
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

        // TODO: shall we chain ack observable with update?
        // TODO: we must handle conflicts somehow like maintaining multiple versions of instanceInfo (per source)
        Observable<Status> updateResult = registry.update(tempNewInfo, deltas, replicationSource);
        updateResult.subscribe(new Subscriber<Status>() {
            @Override
            public void onCompleted() {
                instanceInfoById.put(newInfo.getId(), newInfo);
                sendAckOnTransport();
            }

            @Override
            public void onError(Throwable e) {
                sendErrorOnTransport(e);
            }

            @Override
            public void onNext(Status status) {
                // No op
            }
        });
        return updateResult.ignoreElements().cast(Void.class);
    }

    @Override
    public Observable<Void> unregister(final String instanceId) {
        logger.debug("Removing registration entry for instanceId {}", instanceId);

        if (STATES.Closed == state.get()) {
            /**
             * Since channel is already closed and hence the transport, we don't need to send an error on transport.
             */
            return Observable.error(CHANNEL_CLOSED_EXCEPTION);
        }

        InstanceInfo toUnregister = instanceInfoById.get(instanceId);
        if (toUnregister == null) {
            logger.info("Replicated unregister request for unknown instance {}", instanceId);
            sendAckOnTransport();
            return Observable.empty();
        }

        // TODO: we must handle conflicts somehow like maintaining multiple versions of instanceInfo (per source)
        Observable<Status> updateResult = registry.unregister(toUnregister, replicationSource);
        updateResult.subscribe(new Subscriber<Status>() {
            @Override
            public void onCompleted() {
                instanceInfoById.remove(instanceId);
                sendAckOnTransport();
            }

            @Override
            public void onError(Throwable e) {
                sendErrorOnTransport(e);
            }

            @Override
            public void onNext(Status status) {
                // No op
            }
        });
        return updateResult.ignoreElements().cast(Void.class);
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
