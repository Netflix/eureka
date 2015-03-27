package com.netflix.eureka2.client.functions;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Notification;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public final class ChangeNotificationFunctions {

    private ChangeNotificationFunctions() {
    }

    /**
     * Convert a stream of ChangeNotification<InstanceInfo> to a stream of ChangeNotification<Server> where the service
     * conversion is defined by the given serviceSelector on the InstanceInfo stream.
     *
     * @param serviceSelector a service selector that defines the mapping from a complex instanceInfo to a simple
     *                        host:port server item
     * @return a ChangeNotification stream of servers
     */
    public static final Func1<ChangeNotification<InstanceInfo>, ChangeNotification<Server>> instanceInfoToServer(final ServiceSelector serviceSelector) {
        return new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<Server>>() {
            @Override
            public ChangeNotification<Server> call(ChangeNotification<InstanceInfo> notification) {
                switch (notification.getKind()) {
                    case BufferSentinel:
                        return ChangeNotification.bufferSentinel();  // type change
                    case Add:
                    case Modify:
                    case Delete:
                        Server newServer = instanceInfoToServer(notification.getData());
                        if (newServer != null) {
                            return new ChangeNotification<>(notification.getKind(), newServer);
                        }
                        break;
                    default:
                        // no-op
                }

                return null;
            }

            private Server instanceInfoToServer(InstanceInfo instanceInfo) {
                if (instanceInfo.getStatus() == InstanceInfo.Status.UP) {
                    InetSocketAddress socketAddress = serviceSelector.returnServiceAddress(instanceInfo);
                    if (socketAddress != null) {
                        return new Server(socketAddress.getHostString(), socketAddress.getPort());
                    }
                }
                return null;
            }
        };
    }

    /**
     * Convert change notification stream with buffering sentinels into stream of lists, where each
     * list element contains a batch of data delineated by the markers. Only non-empty lists are
     * issued, which means that for two successive BufferSentinels from the stream, the second
     * one will be swallowed.
     *
     * an onComplete on the change notification stream is considered as another (the last) BufferSentinel.
     * an onError will not return any partial buffered data.
     *
     * @return observable of non-empty list objects
     */
    public static <T> Transformer<ChangeNotification<T>, List<ChangeNotification<T>>> buffers() {
        return new Transformer<ChangeNotification<T>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<ChangeNotification<T>> notifications) {
                final AtomicReference<List<ChangeNotification<T>>> bufferRef = new AtomicReference<>();
                bufferRef.set(new ArrayList<ChangeNotification<T>>());

                return notifications
                        .filter(RxFunctions.filterNullValuesFunc())
                        .materialize()
                        // concatMap as the internal observables all onComplete immediately
                        .concatMap(new Func1<Notification<ChangeNotification<T>>, Observable<List<ChangeNotification<T>>>>() {
                            @Override
                            public Observable<List<ChangeNotification<T>>> call(Notification<ChangeNotification<T>> rxNotification) {
                                List<ChangeNotification<T>> buffer = bufferRef.get();

                                switch (rxNotification.getKind()) {
                                    case OnNext:
                                        ChangeNotification<T> notification = rxNotification.getValue();
                                        if (notification.getKind() == Kind.BufferSentinel) {
                                            bufferRef.set(new ArrayList<ChangeNotification<T>>());
                                            return Observable.just(buffer);
                                        }
                                        buffer.add(notification);
                                        break;
                                    case OnCompleted:
                                        return Observable.just(buffer);  // OnCompleted == BufferSentinel
                                    case OnError:
                                        //clear the buffer and onError, don't return partial error buffer
                                        bufferRef.set(new ArrayList<ChangeNotification<T>>());
                                        return Observable.error(rxNotification.getThrowable());
                                }

                                return Observable.empty();
                            }
                        });
            }
        };
    }

    /**
     * Collapse observable of change notification batches into a set of currently known items.
     * Use a LinkedHashSet to maintain order based on insertion order.
     *
     * Note that the same batch can be emitted multiple times if the transformer receive "empty" prompts
     * from the buffers transformer. Users should apply .distinctUntilChanged() if this is not desired
     * behaviour.
     *
     * @return observable of distinct set objects
     */
    public static <T> Transformer<List<ChangeNotification<T>>, LinkedHashSet<T>> snapshots() {
        return ChangeNotifications.snapshots();
    }
}
