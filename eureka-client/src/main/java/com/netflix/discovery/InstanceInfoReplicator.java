package com.netflix.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.util.RateLimiter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A task for updating and replicating the local instanceinfo to the remote server. Properties of this task are:
 * - configured with a single update thread to guarantee sequential update to the remote server
 * - update tasks can be scheduled on-demand via onDemandUpdate()
 * - task processing is rate limited by burstSize
 * - a new update task is always scheduled automatically after an earlier update task. However if an on-demand task
 *   is started, the scheduled automatic update task is discarded (and a new one will be scheduled after the new
 *   on-demand update).
 *
 *   @author dliu
 */
class InstanceInfoReplicator {
    private static final Logger logger = LoggerFactory.getLogger(InstanceInfoReplicator.class);

    private final DiscoveryClient discoveryClient;
    private final InstanceInfo instanceInfo;

    private final long replicationIntervalMs;
    private final ScheduledExecutorService scheduler;
    
    private final RateLimiter rateLimiter;
    private final int burstSize;
    private final int allowedRatePerMinute;
    private Future scheduledFuture = null;

    InstanceInfoReplicator(DiscoveryClient discoveryClient, InstanceInfo instanceInfo, int replicationIntervalSeconds, int burstSize) {
        this.discoveryClient = discoveryClient;
        this.instanceInfo = instanceInfo;
        this.scheduler = Executors.newScheduledThreadPool(1,
                new ThreadFactoryBuilder()
                        .setNameFormat("DiscoveryClient-InstanceInfoReplicator-%d")
                        .setDaemon(true)
                        .build());
        
        this.rateLimiter = new RateLimiter(TimeUnit.MINUTES);
        this.replicationIntervalMs = TimeUnit.MILLISECONDS.convert(replicationIntervalSeconds, TimeUnit.SECONDS);
        this.burstSize = burstSize;
        this.allowedRatePerMinute = 60 * this.burstSize / replicationIntervalSeconds;
        logger.info("InstanceInfoReplicator onDemand update allowed rate per min is {}", allowedRatePerMinute);
    }

    public synchronized void start(int initialDelayMs) {
        if (scheduledFuture == null) {
            instanceInfo.setIsDirty();  // for initial register
            scheduledFuture = scheduler.scheduleAtFixedRate(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 
                initialDelayMs,
                replicationIntervalMs,
                TimeUnit.MILLISECONDS);
        }
    }

    public synchronized void stop() {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }
    
    public synchronized boolean onDemandUpdate() {
        if (rateLimiter.acquire(burstSize, allowedRatePerMinute)) {
            logger.debug("Executing on-demand update of local InstanceInfo");
            scheduler.submit(new Runnable() {
                @Override
                public void run() {
                    refresh();
                }
            });
            return true;
        } else {
            logger.warn("Ignoring onDemand update due to rate limiter");
            return false;
        }
    }

    @VisibleForTesting
    protected void refresh() {
        try {
            discoveryClient.refreshInstanceInfo();

            Long dirtyTimestamp = instanceInfo.isDirtyWithTime();
            if (dirtyTimestamp != null) {
                discoveryClient.register();
                instanceInfo.unsetIsDirty(dirtyTimestamp);
            }
        } catch (Throwable t) {
            logger.warn("There was a problem with the instance info replicator", t);
        }
    }
}