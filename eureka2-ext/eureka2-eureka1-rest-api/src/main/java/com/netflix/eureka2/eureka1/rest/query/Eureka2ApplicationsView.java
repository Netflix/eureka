package com.netflix.eureka2.eureka1.rest.query;

import java.util.Set;

import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.eureka1.rest.model.Eureka1ModelConverters.toEureka1xApplicationsFromV2Collection;

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
        return toEureka1xApplicationsFromV2Collection(latestSnapshot);
    }
}
