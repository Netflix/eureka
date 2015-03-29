package com.netflix.eureka2.client;

import java.net.InetSocketAddress;
import java.util.LinkedHashSet;
import java.util.List;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import rx.Observable.Transformer;
import rx.functions.Func1;

/**
 * A collection of functions that can be applied to an Eureka interest stream.
 * For more generic versions of some of these functions that may not be InstanceInfo specific,
 * see {@link com.netflix.eureka2.interests.ChangeNotifications}
 *
 * @author Tomasz Bak
 */
public final class InterestFunctions {

    /* package private */ InterestFunctions() {
    }

    /**
     * Convert a stream of ChangeNotification<InstanceInfo> to a stream of ChangeNotification<Server> where the service
     * conversion is defined by the given serviceSelector on the InstanceInfo stream.
     *
     * @param serviceSelector a service selector that defines the mapping from a complex instanceInfo to a simple
     *                        host:port server item
     * @return a ChangeNotification stream of servers
     */
    public final Func1<ChangeNotification<InstanceInfo>, ChangeNotification<Server>> instanceInfoToServer(final ServiceSelector serviceSelector) {
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
    public final Transformer<ChangeNotification<InstanceInfo>, List<ChangeNotification<InstanceInfo>>> buffers() {
        return ChangeNotifications.buffers();
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
    public final Transformer<List<ChangeNotification<InstanceInfo>>, LinkedHashSet<InstanceInfo>> snapshots() {
        return ChangeNotifications.snapshots();
    }
}
