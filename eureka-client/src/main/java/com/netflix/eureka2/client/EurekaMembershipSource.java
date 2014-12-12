package com.netflix.eureka2.client;

import java.util.HashSet;

import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.ServicePort;
import netflix.ocelli.Host;
import netflix.ocelli.MembershipEvent;
import rx.Observable;
import rx.functions.Func1;

import static netflix.ocelli.MembershipEvent.EventType.ADD;
import static netflix.ocelli.MembershipEvent.EventType.REMOVE;

/**
 * @author Nitesh Kant
 */
public class EurekaMembershipSource {

    private final EurekaClient client;
    private static final DefaultMapper defaultMapper = new DefaultMapper();

    public EurekaMembershipSource(ServerResolver eurekaResolver) {
        this.client = Eureka.newClient(eurekaResolver);
    }

    public EurekaMembershipSource(EurekaClient client) {
        this.client = client;
    }

    public Observable<MembershipEvent<Host>> forVip(String... vips) {
        return forInterest(Interests.forVips(vips));
    }

    public Observable<MembershipEvent<Host>> forInterest(Interest<InstanceInfo> interest) {
        return forInterest(interest, defaultMapper);
    }

    public Observable<MembershipEvent<Host>> forInterest(Interest<InstanceInfo> interest,
                                                         final Func1<InstanceInfo, Host> instanceInfoToHost) {
        return client.forInterest(interest)
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<MembershipEvent<Host>>>() {
                    @Override
                    public Observable<MembershipEvent<Host>> call(ChangeNotification<InstanceInfo> notification) {
                        Host host = instanceInfoToHost.call(notification.getData());
                        switch (notification.getKind()) {
                            case Add:
                                return Observable.just(new MembershipEvent<Host>(ADD, host));
                            case Delete:
                                return Observable.just(new MembershipEvent<Host>(REMOVE, host));
                            case Modify:
                                return Observable.just(new MembershipEvent<Host>(REMOVE, host),
                                        new MembershipEvent<Host>(ADD, host));
                            default:
                                return Observable.empty();
                        }
                    }
                });
    }

    protected static class DefaultMapper implements Func1<InstanceInfo, Host> {

        @Override
        public Host call(InstanceInfo instanceInfo) {
            String ipAddress = instanceInfo.getDataCenterInfo().getDefaultAddress().getIpAddress();
            HashSet<ServicePort> servicePorts = instanceInfo.getPorts();
            ServicePort portToUse = servicePorts.iterator().next();
            return new Host(ipAddress, portToUse.getPort());
        }
    }
}
