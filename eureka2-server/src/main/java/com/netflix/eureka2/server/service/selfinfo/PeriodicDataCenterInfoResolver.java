package com.netflix.eureka2.server.service.selfinfo;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.registry.datacenter.DataCenterInfo;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.selector.AddressSelector;
import com.netflix.eureka2.server.config.EurekaInstanceInfoConfig;
import com.netflix.eureka2.server.config.EurekaServerTransportConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * TODO: Model the resolveInterval config property as an changable stream once we integrate with dynamic properties.
 *
 * A datacenter info resolver that periodically refreshes the server datacenter info.
 * This is useful in the cloud when virtualized instances can have changing network information.
 *
 * When an error is encountered at resolve time, the error is ignored and logged, and the resolve is skipped for
 * that round.
 *
 * @author David Liu
 */
public class PeriodicDataCenterInfoResolver extends ChainableSelfInfoResolver {

    private static final Logger logger = LoggerFactory.getLogger(PeriodicDataCenterInfoResolver.class);

    public PeriodicDataCenterInfoResolver(final EurekaInstanceInfoConfig config, final EurekaServerTransportConfig transportConfig) {
        this(config, transportConfig, new Func0<Observable<? extends DataCenterInfo>>() {
            @Override
            public Observable<? extends DataCenterInfo> call() {
                return LocalDataCenterInfo.forDataCenterType(config.getDataCenterType());
            }
        }, Schedulers.io());
    }

    @SuppressWarnings("unchecked")
    /*visible for testing */ PeriodicDataCenterInfoResolver(
            final EurekaInstanceInfoConfig config,
            final EurekaServerTransportConfig transportConfig,
            final Func0<Observable<? extends DataCenterInfo>> dataCenterInfoFunc,
            final Scheduler scheduler) {
        super(Observable.timer(0, config.getDataCenterResolveIntervalSec(), TimeUnit.SECONDS, scheduler)
                        .flatMap(new Func1<Long, Observable<? extends DataCenterInfo>>() {
                            @Override
                            public Observable<? extends DataCenterInfo> call(Long aLong) {
                                logger.debug("Re-resolving datacenter info");

                                Observable returnObservable = dataCenterInfoFunc.call();
                                return returnObservable.onErrorResumeNext(new Func1<Throwable, Observable>() {
                                    @Override
                                    public Observable call(Throwable throwable) {
                                        logger.warn("failed to Resolve datacenter info, skipping this round", throwable);
                                        return Observable.empty();
                                    }
                                });
                            }
                        })
                        .map(new Func1<DataCenterInfo, InstanceInfo.Builder>() {
                            @Override
                            public InstanceInfo.Builder call(DataCenterInfo dataCenterInfo) {

                                // also update the healthcheck urls
                                String address = AddressSelector.selectBy().publicIp(true).or().any().returnNameOrIp(dataCenterInfo.getAddresses());
                                HashSet<String> healthCheckUrls = new HashSet<>();
                                healthCheckUrls.add("http://" + address + ':' + transportConfig.getWebAdminPort() + "/healthcheck");

                                return new InstanceInfo.Builder()
                                        .withHealthCheckUrls(healthCheckUrls)
                                        .withDataCenterInfo(dataCenterInfo);
                            }
                        })
        );
    }
}
