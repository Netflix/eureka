package com.netflix.eureka2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action1;
import rx.subjects.BehaviorSubject;

@Singleton
public class RegistryStream {
    private static Logger log = LoggerFactory.getLogger(RegistryStream.class);
    private final RegistryCache registryCache;
    private AtomicReference<List<RegistryItem>> registryItemsRef = new AtomicReference<>();
    private BehaviorSubject<List<RegistryItem>> registryBehaviorSubject;

    public static class RegistryItem {
        final String instanceId;
        final String app;
        final String vipAddress;
        private String status;

        public RegistryItem(String instanceId, String app, String vipAddress, String status) {
            this.instanceId = instanceId;
            this.app = app;
            this.vipAddress = vipAddress;
            this.status = status;
        }
    }

    public static interface RegistryStreamCallback {
        boolean streamReceived(List<RegistryItem> registryItems);
    }

    @Inject
    public RegistryStream(RegistryCache registryCache) {
        this.registryCache = registryCache;
        registryBehaviorSubject = BehaviorSubject.create();
        startStream();
    }

    public void subscribe(final RegistryStreamCallback registryStreamCallback) {
        registryBehaviorSubject.subscribe(new Subscriber<List<RegistryItem>>() {
            @Override
            public void onCompleted() {
                log.info("Should not get into onCompleted");
            }

            @Override
            public void onError(Throwable e) {
                log.error("OnError in registryBehaviorSubject - ", e);
                this.unsubscribe();
            }

            @Override
            public void onNext(List<RegistryItem> registryItems) {
                try {
                    if (!registryStreamCallback.streamReceived(registryItems)) {
                        this.unsubscribe();
                    }
                } catch (Exception ex) {
                    log.error("Exception in onNext handler - ", ex.getMessage());
                }
            }
        });
    }

    private void startStream() {
        publishRegistryItems();

        Observable.interval(30, TimeUnit.SECONDS).subscribe(new Action1<Long>() {
            @Override
            public void call(Long aLong) {
                publishRegistryItems();
            }
        });
    }

    private void publishRegistryItems() {
        refresh();
        if (registryItemsRef.get().size() > 0) {
            registryBehaviorSubject.onNext(registryItemsRef.get());
        }
    }

    private void refresh() {
        final Map<String, InstanceInfo> regCache = registryCache.getCache();
        List<RegistryItem> registryItemsCurrent = new ArrayList<>();
        for (Map.Entry<String, InstanceInfo> instanceInfo : regCache.entrySet()) {
            registryItemsCurrent.add(new RegistryItem(instanceInfo.getKey(), instanceInfo.getValue().getApp(), instanceInfo.getValue().getVipAddress(), instanceInfo.getValue().getStatus().name()));
        }

        if (isCurrentSnapshotSafeToRefresh(registryItemsCurrent.size())) {
            registryItemsRef.set(registryItemsCurrent);
        }
    }

    private boolean isCurrentSnapshotSafeToRefresh(int newSize) {
        final List<RegistryItem> registryItems = registryItemsRef.get();
        if (registryItems != null) {
            return (newSize > (registryItems.size() * 0.8));
        }
        return true;
    }
}
