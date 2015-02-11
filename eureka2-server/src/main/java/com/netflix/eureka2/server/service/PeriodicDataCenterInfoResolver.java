package com.netflix.eureka2.server.service;

import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;

import java.util.concurrent.TimeUnit;

/**
 * TODO: Model the resolveInterval config property as an changable stream once we integrate with dynamic properties.
 *
 * A datacenter info resolver that periodically refreshes the server datacenter info.
 * This is useful in the cloud when virtualized instances can have changing network information.
 *
 * @author David Liu
 */
public class PeriodicDataCenterInfoResolver extends ChainableSelfInfoResolver {

    private static final Logger logger = LoggerFactory.getLogger(PeriodicDataCenterInfoResolver.class);

    public PeriodicDataCenterInfoResolver(final EurekaCommonConfig config) {
        super(Observable.timer(0, config.getDataCenterResolveIntervalSec(), TimeUnit.SECONDS)
                .flatMap(new Func1<Long, Observable<? extends DataCenterInfo>>() {
                    @Override
                    public Observable<? extends DataCenterInfo> call(Long aLong) {
                        logger.debug("Re-resolving datacenter info");
                        return LocalDataCenterInfo.forDataCenterType(config.getMyDataCenterType());
                    }
                })
                .map(new Func1<DataCenterInfo, InstanceInfo.Builder>() {
                    @Override
                    public InstanceInfo.Builder call(DataCenterInfo dataCenterInfo) {
                        return new InstanceInfo.Builder()
                                .withDataCenterInfo(dataCenterInfo);
                    }
                })
        );
    }
}
