package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import rx.Observable;
import rx.functions.Action1;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class RegistryCache {
    private final EurekaInterestClient interestClient;
    private Map<String, InstanceInfo> cache = new ConcurrentHashMap<>();

    @Inject
    public RegistryCache(EurekaInterestClient interestClient) {
        this.interestClient = interestClient;
        subscribeToEurekaStream();
    }

    public Map<String, InstanceInfo> getCache() {
        return cache;
    }

    private Observable<ChangeNotification<InstanceInfo>> buildEurekaFullRegistryObservable() {
        return interestClient.forInterest(Interests.forFullRegistry());
    }

    private void subscribeToEurekaStream() {
        buildEurekaFullRegistryObservable().retry().doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                clearCache();
            }
        }).subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> instanceInfoChangeNotification) {
                if (instanceInfoChangeNotification.getKind() == ChangeNotification.Kind.Delete) {
                    deleteEntry(instanceInfoChangeNotification.getData());
                } else {
                    addOrModify(instanceInfoChangeNotification.getData());
                }
            }
        });
    }

    private void addOrModify(InstanceInfo instanceInfo) {
        final String instanceId = extractInstanceId(instanceInfo);
        if (!instanceId.isEmpty()) {
            cache.put(instanceId, instanceInfo);
        }
    }

    private void deleteEntry(InstanceInfo instanceInfo) {
        final String instanceId = extractInstanceId(instanceInfo);
        if (!instanceId.isEmpty()) {
            cache.remove(instanceId);
        }
    }

    private static String extractInstanceId(InstanceInfo instanceInfo) {
        if (instanceInfo != null &&
                instanceInfo.getDataCenterInfo() != null &&
                AwsDataCenterInfo.class.isAssignableFrom(instanceInfo.getDataCenterInfo().getClass())) {
            final AwsDataCenterInfo dataCenterInfo = (AwsDataCenterInfo) instanceInfo.getDataCenterInfo();
            return dataCenterInfo.getInstanceId();
        }
        return instanceInfo.getId();
    }

    private void clearCache() {
        cache.clear();
    }

    public static void main(String[] args) {

        final EurekaInterestClient interestClient = Eureka.newInterestClientBuilder()
                .withServerResolver(ServerResolvers.fromHostname("localhost").withPort(13101))
                .build();

        final Observable<ChangeNotification<InstanceInfo>> notificationsObservable =
                interestClient.forInterest(Interests.forFullRegistry());

        final AtomicInteger addCount = new AtomicInteger(0);
        final AtomicInteger updateCount = new AtomicInteger(0);
        final AtomicInteger deleteCount = new AtomicInteger(0);
        notificationsObservable.doOnError(new Action1<Throwable>() {
            @Override
            public void call(Throwable throwable) {
                System.out.println("Exception in eureka registry streaming..." + throwable.getMessage());
            }
        }).toBlocking().forEach(new Action1<ChangeNotification<InstanceInfo>>() {
            @Override
            public void call(ChangeNotification<InstanceInfo> instanceInfoChangeNotification) {
                final String instanceId = extractInstanceId(instanceInfoChangeNotification.getData());
                if (!instanceId.isEmpty()) {
                    if (instanceInfoChangeNotification.getKind() == ChangeNotification.Kind.Add) {
                        addCount.incrementAndGet();
                    } else if (instanceInfoChangeNotification.getKind() == ChangeNotification.Kind.Delete) {
                        deleteCount.incrementAndGet();
                    } else if (instanceInfoChangeNotification.getKind() == ChangeNotification.Kind.Modify) {
                        updateCount.incrementAndGet();
                    }
                    System.out.println(String.format("Counts add %d , update %d, delete %d", addCount.get(), updateCount.get(), deleteCount.get()));
                }
            }
        });

        System.out.println("Total registry addCount " + addCount.get());
    }
}
