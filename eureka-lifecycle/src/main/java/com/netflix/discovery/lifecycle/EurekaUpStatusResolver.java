package com.netflix.discovery.lifecycle;

import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.eventbus.spi.InvalidSubscriberException;
import com.netflix.eventbus.spi.Subscribe;
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
    
    private AtomicReference<InstanceInfo.InstanceStatus> currentStatus = new AtomicReference<InstanceInfo.InstanceStatus>(InstanceInfo.InstanceStatus.UNKNOWN);
    private final EventBus eventBus;
    private final DiscoveryClient client;
    
    /**
     * @param executor
     * @param upStatus
     * @param discoveryClientProvider Provider that returns a discovery client.  We use a provider 
     *  because the DiscoveryClient reference may not exist at bootstrap time
     */
    @Inject
    public EurekaUpStatusResolver(DiscoveryClient client, EventBus eventBus) {
        this.eventBus = eventBus;
        this.client   = client;
    }
    
    @Subscribe
    public void onStatusChange(StatusChangeEvent event) {
        currentStatus.set(event.getStatus());
    }
    
    @PostConstruct
    public void init() {
        try {
            eventBus.registerSubscriber(this);
            
            // Must set the initial status
            currentStatus.compareAndSet(InstanceInfo.InstanceStatus.UNKNOWN, client.getInstanceRemoteStatus());
        } catch (InvalidSubscriberException e) {
            LOG.error("Error registring for discovery status change events.", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        eventBus.unregisterSubscriber(this);
    }
    
    /**
     * @return Get the current instance status
     */
    public InstanceInfo.InstanceStatus getStatus() {
        return currentStatus.get();
    }   
}