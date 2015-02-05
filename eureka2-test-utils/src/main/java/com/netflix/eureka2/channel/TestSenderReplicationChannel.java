package com.netflix.eureka2.channel;

import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author David Liu
 */
public class TestSenderReplicationChannel extends TestChannel<ReplicationChannel, ReplicationHello> implements ReplicationChannel {

    public final Queue<ReplicationItem> replicationItems;

    public TestSenderReplicationChannel(ReplicationChannel delegate, Integer id) {
        super(delegate, id);
        this.replicationItems = new ConcurrentLinkedQueue<>();
    }

    @Override
    public Observable<ReplicationHelloReply> hello(ReplicationHello hello) {
        operations.add(hello);
        return delegate.hello(hello);
    }

    @Override
    public Observable<Void> register(InstanceInfo instanceInfo) {
        replicationItems.add(new ReplicationItem(instanceInfo.getId(), ReplicationItem.Type.Register));
        return delegate.register(instanceInfo);
    }

    @Override
    public Observable<Void> unregister(String instanceId) {
        replicationItems.add(new ReplicationItem(instanceId, ReplicationItem.Type.Unregister));
        return delegate.unregister(instanceId);
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