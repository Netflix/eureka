package com.netflix.discovery;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicLong;

import com.google.inject.Inject;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.eventbus.spi.EventBus;
import com.netflix.eventbus.spi.InvalidSubscriberException;
import com.netflix.eventbus.spi.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton that manages the state of @UpStatus/@DownStatus Supplier<Boolean>
 * and emits status changes to @UpStatus Observable<Boolean>.
 *
 * @author elandau
 *
 */
@Singleton
public class EurekaUpStatusResolver {
    private static Logger LOG = LoggerFactory.getLogger(EurekaUpStatusResolver.class);

    private volatile InstanceInfo.InstanceStatus currentStatus = InstanceInfo.InstanceStatus.UNKNOWN;
    private final EventBus eventBus;
    private final EurekaClient client;
    private final AtomicLong counter = new AtomicLong();

    /**
     * @param client the eurekaClient
     * @param eventBus the eventBus to publish eureka status change events
     */
    @Inject
    public EurekaUpStatusResolver(EurekaClient client, EventBus eventBus) {
        this.eventBus = eventBus;
        this.client = client;
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
