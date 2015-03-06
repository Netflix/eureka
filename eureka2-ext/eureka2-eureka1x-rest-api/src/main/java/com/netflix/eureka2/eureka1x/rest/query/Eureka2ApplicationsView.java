package com.netflix.eureka2.eureka1x.rest.query;

import java.util.Set;

import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.eureka1x.rest.model.Eureka1xDomainObjectModelMapper.EUREKA_1X_MAPPER;

/**
 * @author Tomasz Bak
 */
public class Eureka2ApplicationsView extends AbstractEureka2RegistryView<Applications> {
    public Eureka2ApplicationsView(Observable<ChangeNotification<InstanceInfo>> notifications,
                                   long refreshIntervalMs,
                                   Scheduler scheduler) {
        super(notifications, refreshIntervalMs, scheduler);
        connect();
    }

    public Eureka2ApplicationsView(Observable<ChangeNotification<InstanceInfo>> notifications, long refreshIntervalMs) {
        this(notifications, refreshIntervalMs, Schedulers.computation());
    }

    @Override
    protected Applications updateSnapshot(Set<InstanceInfo> latestSnapshot) {
        return EUREKA_1X_MAPPER.toEureka1xApplications(latestSnapshot);
    }
}
