package com.netflix.eureka2.server.channel;

import com.netflix.eureka2.channel.RetryableServiceChannel;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.server.service.replication.RegistryReplicator;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.functions.Func0;
import rx.schedulers.Schedulers;

/**
 * @author David Liu
 */
public class RetryableSenderReplicationChannel
        extends RetryableServiceChannel<ReplicationChannel>
        implements ReplicationChannel {

    private final RegistryReplicator registryReplicator;
    private final Func0<ReplicationChannel> channelFactory;

    public RetryableSenderReplicationChannel(
            Func0<ReplicationChannel> channelFactory,
            RegistryReplicator registryReplicator,
            long retryInitialDelayMs) {
        this(channelFactory, registryReplicator, retryInitialDelayMs, Schedulers.computation());
    }

    public RetryableSenderReplicationChannel(
            Func0<ReplicationChannel> channelFactory,
            RegistryReplicator registryReplicator,
            long retryInitialDelayMs,
            Scheduler scheduler) {
        super(channelFactory.call(), retryInitialDelayMs, scheduler);
        this.registryReplicator = registryReplicator;
        this.channelFactory = channelFactory;
    }

    @Override
    public Observable<ReplicationHelloReply> hello(ReplicationHello hello) {
        return currentDelegateChannel().hello(hello);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        return currentDelegateChannel().register(instanceInfo);
    }

    @Override
    public Observable<Void> update(InstanceInfo newInfo) {
        return currentDelegateChannel().update(newInfo);
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        return currentDelegateChannel().unregister(instanceId);
    }

    @Override
    protected Observable<ReplicationChannel> reestablish() {
        return Observable.create(new Observable.OnSubscribe<ReplicationChannel>() {
            @Override
            public void call(Subscriber<? super ReplicationChannel> subscriber) {
                try {
                    ReplicationChannel newDelegateChannel = channelFactory.call();
                    registryReplicator.reconnect(newDelegateChannel);
                    subscriber.onNext(newDelegateChannel);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    subscriber.onError(e);
                }
            }
        });
    }

    @Override
    protected void _close() {
        registryReplicator.close();
        super._close();
    }

}
