package com.netflix.eureka2.eureka1x.rest.query;

import java.util.Set;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.eureka1x.rest.model.Eureka1xDomainObjectModelMapper.EUREKA_1X_MAPPER;

/**
 * TODO Eureka 2.x has own ids, while 1.x uses AWS instance id
 *
 * @author Tomasz Bak
 */
public class Eureka2InstanceView extends AbstractEureka2RegistryView<com.netflix.appinfo.InstanceInfo> {

    private static final Logger logger = LoggerFactory.getLogger(Eureka2InstanceView.class);

    public Eureka2InstanceView(Observable<ChangeNotification<InstanceInfo>> notifications, long refreshIntervalMs, Scheduler scheduler) {
        super(notifications, refreshIntervalMs, scheduler);
        connect();
    }

    public Eureka2InstanceView(Observable<ChangeNotification<InstanceInfo>> notifications, long refreshIntervalMs) {
        this(notifications, refreshIntervalMs, Schedulers.computation());
    }

    @Override
    protected com.netflix.appinfo.InstanceInfo updateSnapshot(Set<InstanceInfo> latestSnapshot) {
        if (latestSnapshot.isEmpty()) {
            return null;
        }
        InstanceInfo instanceInfo = latestSnapshot.iterator().next();
        if (latestSnapshot.size() > 1) {
            logger.error("Data consistency issue; two instances found with the same instance id {}", instanceInfo.getId());
        }
        return EUREKA_1X_MAPPER.toEureka1xInstanceInfo(instanceInfo);
    }
}
