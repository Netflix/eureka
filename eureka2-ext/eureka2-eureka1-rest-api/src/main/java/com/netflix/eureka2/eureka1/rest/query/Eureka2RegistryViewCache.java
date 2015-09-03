package com.netflix.eureka2.eureka1.rest.query;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.rest.query.Eureka2FullFetchWithDeltaView.RegistryFetch;
import com.netflix.eureka2.utils.functions.ChangeNotifications;
import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.EurekaRegistryView;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfo;

/**
 * @author Tomasz Bak
 */
public class Eureka2RegistryViewCache {

    private static final Logger logger = LoggerFactory.getLogger(Eureka2RegistryViewCache.class);

    private final EurekaRegistryView<InstanceInfo> registryView;
    private final long refreshIntervalMs;
    private final Scheduler scheduler;

    private final AtomicReference<Eureka2FullFetchWithDeltaView> fullFetchWithDeltaView = new AtomicReference<>();

    public Eureka2RegistryViewCache(EurekaRegistryView<InstanceInfo> registryView,
                                    long refreshIntervalMs) {
        this(registryView, refreshIntervalMs, Schedulers.computation());
    }

    public Eureka2RegistryViewCache(EurekaRegistryView<InstanceInfo> registryView,
                                    long refreshIntervalMs,
                                    Scheduler scheduler) {
        this.registryView = registryView;
        this.refreshIntervalMs = refreshIntervalMs;
        this.scheduler = scheduler;
    }

    public Observable<Applications> findAllApplications() {
        return getLatestFullFetch().map(REGISTRY_FETCH_TO_APPLICATIONS_FUN);
    }

    public Observable<Applications> findAllApplicationsDelta() {
        return getLatestFullFetch().map(REGISTRY_FETCH_TO_DELTA_FUN);
    }

    public Observable<Application> findApplication(final String appName) {
        return findAllApplications().flatMap(new Func1<Applications, Observable<Application>>() {
            @Override
            public Observable<Application> call(Applications applications) {
                Application application = applications.getRegisteredApplications(appName);
                return application == null ? Observable.<Application>empty() : Observable.just(application);
            }
        });
    }

    public Observable<Applications> findApplicationsByVip(final String vipAddress) {
        if (vipAddress == null) {
            return Observable.empty();
        }
        return findApplicationsBy(new Func1<com.netflix.appinfo.InstanceInfo, Boolean>() {
            @Override
            public Boolean call(com.netflix.appinfo.InstanceInfo instanceInfo) {
                return vipAddress.equals(instanceInfo.getVIPAddress());
            }
        });
    }

    public Observable<Applications> findApplicationsBySecureVip(final String secureVipAddress) {
        if (secureVipAddress == null) {
            return Observable.empty();
        }
        return findApplicationsBy(new Func1<com.netflix.appinfo.InstanceInfo, Boolean>() {
            @Override
            public Boolean call(com.netflix.appinfo.InstanceInfo instanceInfo) {
                return secureVipAddress.equals(instanceInfo.getSecureVipAddress());
            }
        });
    }

    private Observable<Applications> findApplicationsBy(final Func1<com.netflix.appinfo.InstanceInfo, Boolean> predicate) {
        return getLatestFullFetch().map(new Func1<RegistryFetch, Applications>() {
            @Override
            public Applications call(RegistryFetch registryFetch) {
                Applications filteredApps = new Applications();
                for (Application application : registryFetch.getApplications().getRegisteredApplications()) {
                    Application filteredApp = new Application(application.getName());
                    for (com.netflix.appinfo.InstanceInfo instanceInfo : application.getInstances()) {
                        if (predicate.call(instanceInfo)) {
                            filteredApp.addInstance(instanceInfo);
                        }
                    }
                    if (!filteredApp.getInstances().isEmpty()) {
                        filteredApps.addApplication(filteredApp);
                    }
                }
                return filteredApps;
            }
        });
    }

    public Observable<com.netflix.appinfo.InstanceInfo> findInstance(String instanceId) {
        return registryView.forInterest(Interests.forInstance(Operator.Equals, instanceId))
                .compose(ChangeNotifications.<InstanceInfo>delineatedBuffers())
                .compose(ChangeNotifications.snapshots(ChangeNotifications.instanceInfoIdentity()))
                .flatMap(new Func1<LinkedHashSet<InstanceInfo>, Observable<com.netflix.appinfo.InstanceInfo>>() {
                    @Override
                    public Observable<com.netflix.appinfo.InstanceInfo> call(LinkedHashSet<InstanceInfo> latestSnapshot) {
                        if (latestSnapshot.isEmpty()) {
                            return Observable.empty();
                        }
                        InstanceInfo instanceInfo = latestSnapshot.iterator().next();
                        if (latestSnapshot.size() > 1) {
                            logger.error("Data consistency issue; two instances found with the same instance id {}", instanceInfo.getId());
                        }
                        return Observable.just(toEureka1xInstanceInfo(latestSnapshot.iterator().next()));
                    }
                });
    }

    private Observable<RegistryFetch> getLatestFullFetch() {
        if (fullFetchWithDeltaView.get() == null) {
            synchronized (fullFetchWithDeltaView) {
                if (fullFetchWithDeltaView.get() == null) {
                    Eureka2FullFetchWithDeltaView newView = new Eureka2FullFetchWithDeltaView(
                            registryView.forInterest(Interests.forFullRegistry()),
                            refreshIntervalMs,
                            scheduler
                    );
                    fullFetchWithDeltaView.set(newView);
                }
            }
        }
        return fullFetchWithDeltaView.get().latestCopy();
    }

    private static final Func1<RegistryFetch, Applications> REGISTRY_FETCH_TO_APPLICATIONS_FUN =
            new Func1<RegistryFetch, Applications>() {
                @Override
                public Applications call(RegistryFetch registryFetch) {
                    return registryFetch.getApplications();
                }
            };

    private static final Func1<RegistryFetch, Applications> REGISTRY_FETCH_TO_DELTA_FUN =
            new Func1<RegistryFetch, Applications>() {
                @Override
                public Applications call(RegistryFetch registryFetch) {
                    return registryFetch.getDeltaChanges();
                }
            };
}
