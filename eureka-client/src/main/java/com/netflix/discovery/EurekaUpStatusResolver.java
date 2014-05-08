package com.netflix.discovery;

import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.netflix.appinfo.InstanceInfo;
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

    private volatile InstanceInfo.InstanceStatus currentStatus = InstanceInfo.InstanceStatus.UNKNOWN;
    private final EventBus eventBus;
    private final DiscoveryClient client;
    private final AtomicLong counter = new AtomicLong();
    
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
        LOG.info("Eureka status changed from " + event.getPreviousStatus() + " to " + event.getStatus());
        currentStatus = event.getStatus();
        counter.incrementAndGet();
    }

    @PostConstruct
    public void init() {
        try {
            // Must set the initial status
            currentStatus = client.getInstanceRemoteStatus();
            
            LOG.info("Initial status set to " + currentStatus);
            eventBus.registerSubscriber(this);
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
        return currentStatus;
    }
    
    public long getChangeCount() {
        return counter.get();
    }
}
