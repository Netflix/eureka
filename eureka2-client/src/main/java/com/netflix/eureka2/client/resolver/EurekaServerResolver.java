package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.Names;
import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.NetworkAddress;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import rx.Observable;
import rx.functions.Func1;

import java.net.InetSocketAddress;

/**
 * @author David Liu
 */
public class EurekaServerResolver extends OcelliServerResolver {
    EurekaServerResolver(Observable<ChangeNotification<InstanceInfo>> instanceInfoSource) {
        super(instanceInfoSource.map(SERVER_TRANSFORM_FUNC));
    }

    private static final Func1<ChangeNotification<InstanceInfo>, ChangeNotification<Server>> SERVER_TRANSFORM_FUNC =
            new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<Server>>() {
                final ServiceSelector serviceSelector = ServiceSelector.selectBy()
                        .serviceLabel(Names.DISCOVERY).protocolType(NetworkAddress.ProtocolType.IPv4).publicIp(true)
                        .or()
                        .serviceLabel(Names.DISCOVERY).protocolType(NetworkAddress.ProtocolType.IPv4);

                @Override
                public ChangeNotification<Server> call (ChangeNotification < InstanceInfo > notification) {
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
                        return new Server(socketAddress.getHostString(), socketAddress.getPort());
                    }
                    return null;
                }
            };
}
