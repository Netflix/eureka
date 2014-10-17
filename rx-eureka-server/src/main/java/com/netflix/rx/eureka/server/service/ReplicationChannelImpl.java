package com.netflix.rx.eureka.server.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.netflix.rx.eureka.protocol.EurekaProtocolError;
import com.netflix.rx.eureka.protocol.Heartbeat;
import com.netflix.rx.eureka.protocol.replication.RegisterCopy;
import com.netflix.rx.eureka.protocol.replication.UnregisterCopy;
import com.netflix.rx.eureka.protocol.replication.UpdateCopy;
import com.netflix.rx.eureka.registry.Delta;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.registry.EurekaRegistry.Origin;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.server.transport.ClientConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;

/**
 *
 * @author Nitesh Kant
 */
public class ReplicationChannelImpl extends AbstractChannel<ReplicationChannelImpl.STATES> implements ReplicationChannel {

    private static final Logger logger = LoggerFactory.getLogger(ReplicationChannelImpl.class);
    private final ReplicationChannelMetrics metrics;

    protected enum STATES {Opened, Closed}

    private final Map<String, InstanceInfo> instanceInfoById = new HashMap<>();

    public ReplicationChannelImpl(ClientConnection transport, EurekaRegistry<InstanceInfo> registry, ReplicationChannelMetrics metrics) {
        super(STATES.Opened, transport, registry, 3, 30000);

        this.metrics = metrics;
        this.metrics.incrementStateCounter(STATES.Opened);

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
                } else if (message instanceof Heartbeat) {
                    heartbeat();
                } else {
                    sendErrorOnTransport(new EurekaProtocolError("Unexpected message " + message));
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

        Observable<Void> registerResult = registry.register(instanceInfo, Origin.REPLICATED);
        registerResult.subscribe(new Subscriber<Void>() {
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
            public void onNext(Void aVoid) {
                // Nothing to do for a void.
            }
        }); // Callers aren't required to subscribe, so it is eagerly subscribed.
        return registerResult;
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

        Set<Delta<?>> deltas = newInfo.diffOlder(currentInfo);
        logger.debug("Set of InstanceInfo modified fields: {}", deltas);

        // TODO: shall we chain ack observable with update?
        // TODO: we must handle conflicts somehow like maintaining multiple versions of instanceInfo (per source)
        Observable<Void> updateResult = registry.update(newInfo, deltas);
        updateResult.subscribe(new Subscriber<Void>() {
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
            public void onNext(Void aVoid) {
                // No op
            }
        });
        return updateResult;
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

        if (!instanceInfoById.containsKey(instanceId)) {
            logger.info("Replicated unregister request for unknown instance {}", instanceId);
            sendAckOnTransport();
            return Observable.empty();
        }

        // TODO: we must handle conflicts somehow like maintaining multiple versions of instanceInfo (per source)
        Observable<Void> updateResult = registry.unregister(instanceId);
        updateResult.subscribe(new Subscriber<Void>() {
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
            public void onNext(Void aVoid) {
                // No op
            }
        });
        return updateResult;
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
