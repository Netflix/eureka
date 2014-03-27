package com.netflix.discovery.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.RefreshCallback;
import com.netflix.discovery.shared.Application;
import com.netflix.governator.guice.lazy.LazySingleton;

/**
 * Singleton that manages the state of @UpStatus/@DownStatus Supplier<Boolean>
 * and emits status changes to @UpStatus Observable<Boolean>.
 * 
 * @author elandau
 *
 */
@LazySingleton
public class EurekaUpStatusResolver  {
    private static Logger LOG = LoggerFactory.getLogger(EurekaUpStatusResolver.class);
    
    private final Provider<DiscoveryClient> discoveryClientProvider;
    private final Provider<InstanceInfo>    instanceInfoProvider;
    private final AtomicBoolean upStatus = new AtomicBoolean(false);
    private final Subject<Boolean, Boolean> subject = BehaviorSubject.createWithDefaultValue(false);
    
    /**
     * @param executor
     * @param upStatus
     * @param discoveryClientProvider Provider that returns a discovery client.  We use a provider 
     *  because the DiscoveryClient reference may not exist at bootstrap time
     */
    @Inject
    public EurekaUpStatusResolver(
            Provider<DiscoveryClient> discoveryClientProvider,
            Provider<InstanceInfo>    instanceInfoProvider) {
        this.discoveryClientProvider = discoveryClientProvider;
        this.instanceInfoProvider = instanceInfoProvider;
    }
    
    @PostConstruct
    public void init() {
        final InstanceInfo self = instanceInfoProvider.get();
        
        discoveryClientProvider.get().registerRefreshCallback(new RefreshCallback() {
            @Override
            public void postRefresh(DiscoveryClient discoveryClient) {
                for (Application app : discoveryClient.getApplications().getRegisteredApplications()) {
                    InstanceInfo remoteSelf = app.getByInstanceId(self.getId());
                    if (remoteSelf != null) {
                        setIsUp(remoteSelf.getStatus().equals(InstanceStatus.UP));
                        return;
                    }
                }
                setIsUp(false);
            }
            
            private void setIsUp(boolean isUp) {
                if (upStatus.compareAndSet(!isUp, isUp)) {
                    LOG.info("Discovery upStatus change to " + isUp);
                    subject.onNext(isUp);
                }
            }
        });
    }
    
    @PreDestroy
    public void shutdown() {
    }
    
    public AtomicBoolean getUpStatus() {
        return upStatus;
    }

    public Boolean isUp() {
        return upStatus.get();
    }

    public Observable<Boolean> asObservable() {
        return subject;
    }
}