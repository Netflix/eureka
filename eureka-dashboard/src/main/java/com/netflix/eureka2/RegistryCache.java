package com.netflix.eureka2;
import com.google.inject.Singleton;
import com.google.inject.Inject;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.DataCenterInfo;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.AwsDataCenterInfo;
import com.netflix.eureka2.registry.datacenter.BasicDataCenterInfo;
import rx.Observable;
import rx.Subscriber;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class RegistryCache {
    private final EurekaClient eurekaClient;
    private Map<String, InstanceInfo> cache = new ConcurrentHashMap<>();

    @Inject
    public RegistryCache(DashboardEurekaClientBuilder dashboardEurekaClientBuilder) {
        eurekaClient = dashboardEurekaClientBuilder.getEurekaClient();
        subscribeToEurekaStream();
    }

    public Map<String, InstanceInfo> getCache() {
        return cache;
    }

    private Observable<ChangeNotification<InstanceInfo>> buildEurekaFullRegistryObservable() {
        return eurekaClient.forInterest(Interests.forFullRegistry());
    }

    private void subscribeToEurekaStream() {
        buildEurekaFullRegistryObservable().subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> instanceInfoChangeNotification) {
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
        if (! instanceId.isEmpty()) {
            cache.put(instanceId, instanceInfo);
        }
    }

    private void deleteEntry(InstanceInfo instanceInfo) {
        final String instanceId = extractInstanceId(instanceInfo);
        if (! instanceId.isEmpty()) {
            cache.remove(instanceId);
        }
    }

    private String extractInstanceId(InstanceInfo instanceInfo) {
        if (AwsDataCenterInfo.class.isAssignableFrom(instanceInfo.getDataCenterInfo().getClass())) {
            final AwsDataCenterInfo dataCenterInfo = (AwsDataCenterInfo) instanceInfo.getDataCenterInfo();
            return dataCenterInfo.getInstanceId();
        }
        return "";
    }

    private void clearCache() {
        cache.clear();
    }

}
