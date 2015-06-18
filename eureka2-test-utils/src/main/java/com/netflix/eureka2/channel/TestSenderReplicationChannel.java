package com.netflix.eureka2.channel;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.protocol.replication.ReplicationHello;
import com.netflix.eureka2.protocol.replication.ReplicationHelloReply;
import com.netflix.eureka2.registry.Source;
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
    public Observable<Void> replicate(final ChangeNotification<InstanceInfo> notification) {
        return delegate.replicate(notification)
                .doOnCompleted(new Action0() {
                    @Override
                    public void call() {
                        switch (notification.getKind()) {
                            case Add:
                            case Modify:
                                replicationItems.add(new ReplicationItem(notification.getData().getId(), ReplicationItem.Type.Register));
                                break;
                            case Delete:
                                replicationItems.add(new ReplicationItem(notification.getData().getId(), ReplicationItem.Type.Unregister));
                                break;
                            case BufferSentinel:
                                break;   // no op
                            default:
                                throw new IllegalStateException("Unexpected state");
                        }
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        switch (notification.getKind()) {
                            case Add:
                            case Modify:
                                failedReplicationItems.add(new ReplicationItem(notification.getData().getId(), ReplicationItem.Type.Register));
                                break;
                            case Delete:
                                failedReplicationItems.add(new ReplicationItem(notification.getData().getId(), ReplicationItem.Type.Unregister));
                                break;
                            case BufferSentinel:
                                break;   // no op
                            default:
                                throw new IllegalStateException("Unexpected state");
                        }
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

    @Override
    public Source getSource() {
        return new Source(Source.Origin.REPLICATED, "test");
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