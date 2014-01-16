package com.netflix.discovery;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.governator.annotations.Configuration;
import com.netflix.governator.annotations.binding.Background;
import com.netflix.governator.annotations.binding.UpStatus;

/**
 * Background task for checking the up status of the instance in discovery.
 * It's important to check against the instance's status in discovery instead
 * of an internally managed state since an instance can be disabled externally
 * in Eureka.
 * 
 * @author elandau
 *
 */
@Singleton
public class DiscoveryStatusChecker {
    private static Logger LOG = LoggerFactory.getLogger(DiscoveryStatusChecker.class);
    
    private final ScheduledExecutorService executor;
    private final Provider<DiscoveryClient> discoveryClientProvider;
    private final Provider<InstanceInfo> instanceInfoProvider;
    private final AtomicBoolean upStatus;
    
    @Configuration(value="discovery.status.interval")
    private long interval = 1;
    
    private ScheduledFuture<?> future = null;
    
    /**
     * @param executor
     * @param upStatus
     * @param discoveryClientProvider Provider that returns a discovery client.  We use a provider 
     *  because the DiscoveryClient reference may not exist at bootstrap time
     */
    @Inject
    public DiscoveryStatusChecker(
            @Background ScheduledExecutorService executor,
            @UpStatus AtomicBoolean upStatus,
            Provider<DiscoveryClient> discoveryClientProvider,
            Provider<InstanceInfo> instanceInfoProvider) {
        this.executor = executor;
        this.discoveryClientProvider = discoveryClientProvider;
        this.upStatus = upStatus;
        this.instanceInfoProvider = instanceInfoProvider;
    }
    
    @PostConstruct
    public void init() {
        LOG.info("Updating internal instance discovery up status every " + interval + " seconds");
        
        updateInstanceUpStatus();
        this.future = executor.scheduleAtFixedRate(new Runnable(){
            @Override
            public void run() {
                updateInstanceUpStatus();
            }}, interval, interval, TimeUnit.SECONDS);
    }
    
    @PreDestroy
    public void shutdown() {
        if (future != null)
            future.cancel(true);
    }
    
    private void updateInstanceUpStatus() {
        DiscoveryClient client = discoveryClientProvider.get();
        if (client != null) {
            final InstanceInfo instanceInfo = instanceInfoProvider.get();
            if (instanceInfo != null) {
                List<InstanceInfo> discoveryInstanceInfoList = client.getInstancesById(instanceInfo.getId());
                final boolean currentStatus;
                if ((discoveryInstanceInfoList != null) && (discoveryInstanceInfoList.size() == 1)) {
                    InstanceInfo discoveryInstanceInfo = discoveryInstanceInfoList.get(0);
                    currentStatus = InstanceStatus.UP.equals(discoveryInstanceInfo.getStatus());
                }
                else {
                    LOG.info("Instance not found");
                    currentStatus = false;
                }
                
                LOG.info("Discovery upStatus=" + currentStatus);
                
                if (upStatus.compareAndSet(!currentStatus, currentStatus)) {
                    // TODO: Add subscribers
                }
            }
            else {
                LOG.info("Discovery InstanceInfo is null");
            }
        }
        else {
            LOG.info("Discovery client is null");
        }
    }
}