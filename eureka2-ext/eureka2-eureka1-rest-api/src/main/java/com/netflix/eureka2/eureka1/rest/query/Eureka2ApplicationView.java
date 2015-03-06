package com.netflix.eureka2.eureka1.rest.query;

import java.util.Set;

import com.netflix.discovery.shared.Application;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.eureka1.rest.model.Eureka1DomainObjectModelMapper.EUREKA_1_MAPPER;

/**
 * @author Tomasz Bak
 */
public class Eureka2ApplicationView extends AbstractEureka2RegistryView<Application> {

    private final String applicationName;

    public Eureka2ApplicationView(String applicationName,
                                  Observable<ChangeNotification<InstanceInfo>> notifications,
                                  long refreshIntervalMs,
                                  Scheduler scheduler) {
        super(notifications, refreshIntervalMs, scheduler);
        this.applicationName = applicationName;
        connect();
    }

    public Eureka2ApplicationView(String applicationName,
                                  Observable<ChangeNotification<InstanceInfo>> notifications,
                                  long refreshIntervalMs) {
        this(applicationName, notifications, refreshIntervalMs, Schedulers.computation());
    }

    @Override
    protected Application updateSnapshot(Set<InstanceInfo> latestSnapshot) {
        return EUREKA_1_MAPPER.toEureka1xApplication(applicationName, latestSnapshot);
    }
}
