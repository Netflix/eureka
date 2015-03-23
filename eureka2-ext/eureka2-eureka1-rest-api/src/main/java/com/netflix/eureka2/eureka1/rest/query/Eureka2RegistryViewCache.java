package com.netflix.eureka2.eureka1.rest.query;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.rest.query.Eureka2FullFetchWithDeltaView.RegistryFetch;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static com.netflix.eureka2.eureka1.rest.model.Eureka1ModelConverters.toEureka1xInstanceInfo;

/**
 * @author Tomasz Bak
 */
public class Eureka2RegistryViewCache {

    private static final Logger logger = LoggerFactory.getLogger(Eureka2RegistryViewCache.class);

    private static final long DEFAULT_REFRESH_INTERVAL_MS = 30000;

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final long refreshIntervalMs;
    private final Scheduler scheduler;

    private final AtomicReference<Eureka2FullFetchWithDeltaView> fullFetchWithDeltaView = new AtomicReference<>();
    private final Map<String, Eureka2ApplicationView> applicationMap = new ConcurrentHashMap<>();
    private final Map<String, Eureka2ApplicationsView> vipMap = new ConcurrentHashMap<>();
    private final Map<String, Eureka2ApplicationsView> secureVipMap = new ConcurrentHashMap<>();

    public Eureka2RegistryViewCache(SourcedEurekaRegistry<InstanceInfo> registry) {
        this(registry, DEFAULT_REFRESH_INTERVAL_MS, Schedulers.computation());
    }

    public Eureka2RegistryViewCache(SourcedEurekaRegistry<InstanceInfo> registry,
                                    long refreshIntervalMs) {
        this(registry, refreshIntervalMs, Schedulers.computation());
    }

    public Eureka2RegistryViewCache(SourcedEurekaRegistry<InstanceInfo> registry,
                                    long refreshIntervalMs,
                                    Scheduler scheduler) {
        this.registry = registry;
        this.refreshIntervalMs = refreshIntervalMs;
        this.scheduler = scheduler;
    }

    public Observable<Applications> findAllApplications() {
        return getLatestFullFetch().map(REGISTRY_FETCH_TO_APPLICATIONS_FUN);
    }

    public Observable<Applications> findAllApplicationsDelta() {
        return getLatestFullFetch().map(REGISTRY_FETCH_TO_DELTA_FUN);
    }

    public Observable<Application> findApplication(String appName) {
        if (applicationMap.get(appName) == null) {
            synchronized (applicationMap) {
                if (applicationMap.get(appName) == null) {
                    Eureka2ApplicationView applicationView = new Eureka2ApplicationView(
                            appName,
                            registry.forInterest(Interests.forApplications(Operator.Equals, appName)),
                            refreshIntervalMs,
                            scheduler);
                    applicationMap.put(appName, applicationView);
                }
            }
        }
        return applicationMap.get(appName).latestCopy();
    }

    public Observable<Applications> findApplicationsByVip(String vipAddress) {
        return findApplicationsBy(vipAddress, Interests.forVips(vipAddress), vipMap);
    }

    public Observable<Applications> findApplicationsBySecureVip(final String secureVipAddress) {
        return findApplicationsBy(secureVipAddress, Interests.forSecureVips(secureVipAddress), secureVipMap);
    }

    private Observable<Applications> findApplicationsBy(String id, Interest<InstanceInfo> interest, Map<String, Eureka2ApplicationsView> cache) {
        if (cache.get(id) == null) {
            synchronized (cache) {
                if (cache.get(id) == null) {
                    Eureka2ApplicationsView applicationsView = new Eureka2ApplicationsView(
                            registry.forInterest(interest),
                            refreshIntervalMs,
                            scheduler);
                    cache.put(id, applicationsView);
                }
            }
        }
        return cache.get(id).latestCopy();
    }

    public Observable<com.netflix.appinfo.InstanceInfo> findInstance(String instanceId) {
        return registry.forInterest(Interests.forInstance(Operator.Equals, instanceId))
                .compose(ChangeNotifications.<InstanceInfo>delineatedBuffers())
                .compose(ChangeNotifications.<InstanceInfo>snapshots())
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
                            registry.forInterest(Interests.forFullRegistry()),
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
