package com.netflix.eureka2.server.channel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.netflix.eureka2.channel.ReplicationChannel;
import com.netflix.eureka2.channel.ReplicationChannel.STATE;
import com.netflix.eureka2.config.SystemConfigLoader;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.metric.server.ReplicationChannelMetrics;
import com.netflix.eureka2.protocol.EurekaProtocolError;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.DeleteInstance;
import com.netflix.eureka2.protocol.common.StreamStateUpdate;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.SelfInfoResolver;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.BehaviorSubject;

/**
 *
 * @author Nitesh Kant
 */
public class ReceiverReplicationChannel extends AbstractHandlerChannel<STATE> implements ReplicationChannel {

    private static final Logger logger = LoggerFactory.getLogger(ReceiverReplicationChannel.class);

    static final Exception IDLE_STATE_EXCEPTION = new Exception("Replication channel in idle state");
    static final Exception HANDSHAKE_FINISHED_EXCEPTION = new Exception("Handshake already done");
    static final Exception REPLICATION_LOOP_EXCEPTION = new Exception("Self replicating to itself");

    private final SelfInfoResolver selfIdentityService;
    private final SourcedEurekaRegistry<InstanceInfo> registry;

    // FIXME: get rid of the artificially timer induced delay on the stream state notification
    private final BehaviorSubject<StreamStateNotification<InstanceInfo>> streamStateSubject;

    private final long bufferHintDelayMs = SystemConfigLoader
            .getFromSystemPropertySafe("eureka.hacks.receiverReplicationChannel.bufferHintDelayMs", 100);
    private final long maxBufferHintDelayMs = SystemConfigLoader
            .getFromSystemPropertySafe("eureka.hacks.receiverReplicationChannel.maxBufferHintDelayMs", 5000);
    private final AtomicLong delayCounter = new AtomicLong(0l);

    private Source replicationSource;

    // A loop is detected by comparing hello message source id with local instance id.
    private volatile boolean replicationLoop;

    private final ConcurrentHashMap<String, InstanceInfo> instanceInfoById = new ConcurrentHashMap<>();

    public ReceiverReplicationChannel(MessageConnection transport,
                                      SelfInfoResolver selfIdentityService,
                                      SourcedEurekaRegistry<InstanceInfo> registry,
                                      ReplicationChannelMetrics metrics) {
        super(STATE.Idle, transport, metrics);
        this.selfIdentityService = selfIdentityService;
        this.registry = registry;
        this.streamStateSubject = BehaviorSubject.create();

        subscribeToTransportInput(new Action1<Object>() {
            @Override
            public void call(Object message) {
                dispatchMessageFromClient(message);
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
            logger.info("Received Hello from {}", ((ReplicationHello) message).getSource());
            reply = hello((ReplicationHello) message);
        } else if (message instanceof AddInstance) {
            InstanceInfo instanceInfo = ((AddInstance) message).getInstanceInfo();
            reply = register(instanceInfo);// No need to subscribe, the register() call does the subscription.
            delayCounter.addAndGet(bufferHintDelayMs);
        } else if (message instanceof DeleteInstance) {
            reply = unregister(((DeleteInstance) message).getInstanceId());// No need to subscribe, the unregister() call does the subscription.
            delayCounter.addAndGet(bufferHintDelayMs);
        } else if (message instanceof StreamStateUpdate) {
            final StreamStateUpdate streamStateUpdate = (StreamStateUpdate) message;
            if (streamStateUpdate.getState() == StreamStateNotification.BufferState.BufferStart) {
                delayCounter.set(0);  // reset to 0 for buffer start
            }

            long delay = Math.min(delayCounter.getAndSet(0), maxBufferHintDelayMs);
            reply = Observable.timer(delay, TimeUnit.MILLISECONDS)
                    .doOnNext(new Action1<Long>() {
                        @Override
                        public void call(Long aLong) {
                            streamStateSubject.onNext(streamStateUpdateToStreamStateNotification(streamStateUpdate));
                        }
                    }).ignoreElements();
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

        if(!moveToState(STATE.Idle, STATE.Handshake)) {
            return Observable.error(state.get() == STATE.Closed ? CHANNEL_CLOSED_EXCEPTION : HANDSHAKE_FINISHED_EXCEPTION);
        }

        replicationSource = hello.getSource();

        return selfIdentityService.resolve().take(1).flatMap(new Func1<InstanceInfo, Observable<ReplicationHelloReply>>() {
            @Override
            public Observable<ReplicationHelloReply> call(InstanceInfo instanceInfo) {
                replicationLoop = instanceInfo.getId().equals(hello.getSource().getName());

                Source replySource = new Source(Source.Origin.REPLICATED, instanceInfo.getId(), hello.getSource().getId());
                ReplicationHelloReply reply = new ReplicationHelloReply(replySource, false);
                sendOnTransport(reply);
                moveToState(STATE.Handshake, STATE.Connected);
                return Observable.just(reply);
            }
        });
    }

    // FIXME maybe it's time that the sender and receiver abstractions are separated.
    @Override
    public Observable<Void> replicate(ChangeNotification<InstanceInfo> notification) {
        return Observable.error(new UnsupportedOperationException("Not implemented for receiver"));
    }

    public Observable<StreamStateNotification<InstanceInfo>> getStreamStateNotifications() {
        return streamStateSubject.asObservable();
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
    private Observable<Void> register(final InstanceInfo instanceInfo) {
        logger.debug("Replicated registry entry: {}", instanceInfo);

        if (STATE.Connected != state.get()) {
            return Observable.error(state.get() == STATE.Closed ? CHANNEL_CLOSED_EXCEPTION : IDLE_STATE_EXCEPTION);
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
    private Observable<Void> unregister(final String instanceId) {
        logger.debug("Removing registration entry for instanceId {}", instanceId);

        if (STATE.Connected != state.get()) {
            return Observable.error(state.get() == STATE.Closed ? CHANNEL_CLOSED_EXCEPTION : IDLE_STATE_EXCEPTION);
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

    private StreamStateNotification<InstanceInfo> streamStateUpdateToStreamStateNotification(StreamStateUpdate notification) {
        StreamStateNotification.BufferState state = notification.getState();
        if (state == StreamStateNotification.BufferState.BufferStart || state == StreamStateNotification.BufferState.BufferEnd) {
            return new StreamStateNotification<>(state, notification.getInterest());
        }
        throw new IllegalStateException("Unexpected state " + state);
    }

    @Override
    protected void _close() {
        if(state.get() == STATE.Closed) {
            return;
        }
        moveToState(STATE.Closed);
        super._close();
        unregisterAll();
        streamStateSubject.onCompleted();
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
