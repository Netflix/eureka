package com.netflix.eureka.server.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka.datastore.Item;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.interests.ModifyNotification;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.service.InterestChannel;
import com.netflix.eureka.service.RegistrationChannel;
import rx.Observable;
import rx.functions.Func1;

import java.util.Set;

/**
 * @author Nitesh Kant
 */
@Singleton
public class EurekaServiceImpl implements EurekaServerService {

    private final EurekaRegistry registry;

    @Inject
    public EurekaServiceImpl(EurekaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public InterestChannel forInterest(Interest<InstanceInfo> interest) {
        Observable<ChangeNotification<InstanceInfo>> stream = registry.forInterest(interest);
        // TODO: optimize optimize
        return new InterestChannelImpl(stream.flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<? extends ChangeNotification<? extends Item>>>() {
            @Override
            public Observable<? extends ChangeNotification<? extends Item>> call(ChangeNotification<InstanceInfo> notification) {
                switch (notification.getKind()) {
                    case Add:
                    case Delete:
                        return Observable.from(notification);
                    case Modify:
                        ModifyNotification<InstanceInfo> modifyNotification = (ModifyNotification<InstanceInfo>) notification;
                        Set<Delta<?>> deltas = modifyNotification.getData().diffOlder(modifyNotification.getPrev());
                        return Observable.from(deltas).map(new Func1<Delta<?>, ChangeNotification<? extends Item>>() {
                            @Override
                            public ChangeNotification<? extends Item> call(Delta<?> delta) {
                                return new ChangeNotification<Delta<?>>(ChangeNotification.Kind.Modify, delta);
                            }
                        });
                }
                return Observable.empty();
            }
        }));
    }

    @Override
    public RegistrationChannel newRegistrationChannel() {
        return new RegistrationChannelImpl(registry);
    }

    @Override
    public ReplicationChannel newReplicationChannel(InstanceInfo sourceServer) {
        return new ReplicationChannelImpl(sourceServer);
    }
}
