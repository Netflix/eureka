package com.netflix.eureka2.channel;

import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author David Liu
 */
public class TestSenderReplicationChannel extends TestChannel<ReplicationChannel, ReplicationHello> implements ReplicationChannel {

    public final Queue<ReplicationItem> replicationItems;
    public final Queue<ReplicationItem> failedReplicationItems;

    public TestSenderReplicationChannel(ReplicationChannel delegate, Integer id) {
        super(delegate, id);
        this.replicationItems = new ConcurrentLinkedQueue<>();
        this.failedReplicationItems = new ConcurrentLinkedQueue<>();
    }

    @Override
    public Observable<ReplicationHelloReply> hello(ReplicationHello hello) {
        operations.add(hello);
        return delegate.hello(hello);
    }

    @Override
    public Observable<Void> register(final InstanceInfo instanceInfo) {
        return delegate.register(instanceInfo)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        replicationItems.add(new ReplicationItem(instanceInfo.getId(), ReplicationItem.Type.Register));
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        failedReplicationItems.add(new ReplicationItem(instanceInfo.getId(), ReplicationItem.Type.Register));
                    }
                });
    }

    @Override
    public Observable<Void> unregister(final String instanceId) {
        return delegate.unregister(instanceId)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        replicationItems.add(new ReplicationItem(instanceId, ReplicationItem.Type.Unregister));
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        failedReplicationItems.add(new ReplicationItem(instanceId, ReplicationItem.Type.Unregister));
                    }
                });
    }

    // poll and wait until the number of replication items are received or the timeout is reached
    public boolean awaitReplicationItems(int itemCount, int timeoutMillis) throws Exception {
        long timeoutTime = System.currentTimeMillis() + timeoutMillis;
        while(System.currentTimeMillis() < timeoutTime) {
            if (replicationItems.size() == itemCount) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }

    public static class ReplicationItem {
        public enum Type {Register, Unregister};
        public final String id;
        public final Type type;
        public ReplicationItem(String id, Type type) {
            this.id = id;
            this.type = type;
        }
        @Override
        public String toString() {
            return type + ":" + id;
        }
    }
}