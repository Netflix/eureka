package com.netflix.eureka2.eureka1x.rest.query;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interest.Operator;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Scheduler;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class Eureka2RegistryViewCache {

    private static final long DEFAULT_REFRESH_INTERVAL_MS = 30000;
    private static final long DEFAULT_QUERY_TIMEOUT_MS = 30000;

    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final long refreshIntervalMs;
    private final long queryTimeoutMs;
    private final Scheduler scheduler;

    private final AtomicReference<Eureka2ApplicationsView> allApplicationsView = new AtomicReference<>();
    private final Map<String, Eureka2ApplicationView> applicationMap = new ConcurrentHashMap<>();
    private final Map<String, Eureka2ApplicationsView> vipMap = new ConcurrentHashMap<>();
    private final Map<String, Eureka2ApplicationsView> secureVipMap = new ConcurrentHashMap<>();
    private final Map<String, Eureka2InstanceView> instanceViewMap = new ConcurrentHashMap<>();

    public Eureka2RegistryViewCache(SourcedEurekaRegistry<InstanceInfo> registry) {
        this(registry, DEFAULT_REFRESH_INTERVAL_MS, DEFAULT_QUERY_TIMEOUT_MS, Schedulers.computation());
    }

    public Eureka2RegistryViewCache(SourcedEurekaRegistry<InstanceInfo> registry,
                                    long refreshIntervalMs,
                                    long queryTimeoutMs) {
        this(registry, refreshIntervalMs, queryTimeoutMs, Schedulers.computation());
    }

    public Eureka2RegistryViewCache(SourcedEurekaRegistry<InstanceInfo> registry,
                                    long refreshIntervalMs,
                                    long queryTimeoutMs,
                                    Scheduler scheduler) {
        this.registry = registry;
        this.refreshIntervalMs = refreshIntervalMs;
        this.queryTimeoutMs = queryTimeoutMs;
        this.scheduler = scheduler;
    }

    public Applications findAllApplications() {
        if (allApplicationsView.get() == null) {
            Eureka2ApplicationsView newView = new Eureka2ApplicationsView(
                    registry.forInterest(Interests.forFullRegistry()),
                    refreshIntervalMs,
                    scheduler
            );
            if (!allApplicationsView.compareAndSet(null, newView)) {
                newView.close();
            }
        }
        return allApplicationsView.get().latestCopy().timeout(queryTimeoutMs, TimeUnit.MILLISECONDS).take(1).toBlocking().first();
    }

    public Application findApplication(String appName) {
        if (applicationMap.get(appName) == null) {
            Eureka2ApplicationView applicationView = new Eureka2ApplicationView(
                    appName,
                    registry.forInterest(Interests.forApplications(Operator.Equals, appName)),
                    refreshIntervalMs,
                    scheduler);
            synchronized (applicationMap) {
                if (applicationMap.get(appName) == null) {
                    applicationMap.put(appName, applicationView);
                }
            }
        }
        return applicationMap.get(appName).latestCopy().timeout(queryTimeoutMs, TimeUnit.MILLISECONDS).take(1).toBlocking().first();
    }

    public Applications findApplicationsByVip(String vipAddress) {
        return findApplicationsBy(vipAddress, Interests.forVips(vipAddress), vipMap);
    }

    public Applications findApplicationsBySecureVip(final String secureVipAddress) {
        return findApplicationsBy(secureVipAddress, Interests.forSecureVips(secureVipAddress), secureVipMap);
    }

    private Applications findApplicationsBy(String id, Interest<InstanceInfo> interest, Map<String, Eureka2ApplicationsView> cache) {
        if (cache.get(id) == null) {
            Eureka2ApplicationsView applicationsView = new Eureka2ApplicationsView(
                    registry.forInterest(interest),
                    refreshIntervalMs,
                    scheduler);
            synchronized (cache) {
                if (cache.get(id) == null) {
                    cache.put(id, applicationsView);
                }
            }
        }
        return cache.get(id).latestCopy().timeout(queryTimeoutMs, TimeUnit.MILLISECONDS).take(1).toBlocking().first();
    }

    public com.netflix.appinfo.InstanceInfo findInstance(String instanceId) {
        if (instanceViewMap.get(instanceId) == null) {
            Eureka2InstanceView applicationView = new Eureka2InstanceView(
                    registry.forInterest(Interests.forInstance(Operator.Equals, instanceId)),
                    refreshIntervalMs,
                    scheduler);
            synchronized (instanceViewMap) {
                if (instanceViewMap.get(instanceId) == null) {
                    instanceViewMap.put(instanceId, applicationView);
                }
            }
        }
        return instanceViewMap.get(instanceId).latestCopy().timeout(queryTimeoutMs, TimeUnit.MILLISECONDS).take(1).toBlocking().first();
    }
}
