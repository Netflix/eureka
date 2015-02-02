package com.netflix.eureka2.client.channel;

import com.netflix.eureka2.client.transport.tcp.TcpDiscoveryClient;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.protocol.discovery.AddInstance;
import com.netflix.eureka2.protocol.discovery.SnapshotComplete;
import com.netflix.eureka2.protocol.discovery.SnapshotRegistration;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.transport.MessageConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class SnapshotInterestChannelImpl implements SnapshotInterestChannel {

    private static final Logger logger = LoggerFactory.getLogger(SnapshotInterestChannel.class);

    private final TcpDiscoveryClient transportClient;

    public SnapshotInterestChannelImpl(TcpDiscoveryClient transportClient) {
        this.transportClient = transportClient;
    }

    @Override
    public Observable<InstanceInfo> forSnapshot(final Interest<InstanceInfo> interest) {
        return transportClient.connect()
                .flatMap(new Func1<MessageConnection, Observable<InstanceInfo>>() {
                    @Override
                    public Observable<InstanceInfo> call(final MessageConnection connection) {
                        return connection
                                // Subscribe to interest set
                                .submitWithAck(new SnapshotRegistration(interest))
                                .cast(Object.class)
                                .concatWith(connection.incoming())
                                        // Handle change notification stream
                                .takeWhile(END_OF_STREAM_FILTER)
                                .filter(INVALID_MESSAGE_FILTER)
                                .doOnNext(new Action1<Object>() {
                                    @Override
                                    public void call(Object message) {
                                        connection.acknowledge().doOnError(new Action1<Throwable>() {
                                            @Override
                                            public void call(Throwable e) {
                                                logger.error("Failed to send an acknowledgment to snapshot change notification message", e);
                                            }
                                        }).subscribe();
                                    }
                                })
                                .map(new Func1<Object, InstanceInfo>() {
                                    @Override
                                    public InstanceInfo call(Object message) {
                                        return ((AddInstance) message).getInstanceInfo();
                                    }
                                })
                                        // Cleanup resources
                                .doOnError(new Action1<Throwable>() {
                                    @Override
                                    public void call(Throwable error) {
                                        logger.error("Snapshot subscription error", error);
                                    }
                                })
                                .doOnTerminate(new Action0() {
                                    @Override
                                    public void call() {
                                        connection.shutdown();
                                    }
                                });
                    }
                });
    }

    public static final Func1<Object, Boolean> END_OF_STREAM_FILTER = new Func1<Object, Boolean>() {
        @Override
        public Boolean call(Object message) {
            return !(message instanceof SnapshotComplete);
        }
    };

    private static final Func1<Object, Boolean> INVALID_MESSAGE_FILTER = new Func1<Object, Boolean>() {
        @Override
        public Boolean call(Object message) {
            boolean isKnown = message instanceof AddInstance;
            if (!isKnown) {
                logger.warn("Unrecognized discovery protocol message of type " + message.getClass());
            }
            return isKnown;
        }
    };

    @Override
    public void close() {
        // This is stateless channel. Transport connection is automatically disconnected
        // when the snapshot stream completes.
    }

    @Override
    public void close(Throwable error) {
        // This is stateless channel. Transport connection is automatically disconnected
        // when the snapshot stream completes.
    }

    @Override
    public Observable<Void> asLifecycleObservable() {
        return Observable.never();
    }
}
