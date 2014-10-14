package com.netflix.rx.eureka.server.service;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.server.BridgeServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author David Liu
 */
@Singleton
public class BridgeService {
    private static final Logger logger = LoggerFactory.getLogger(BridgeService.class);

    private final BridgeServerConfig config;
    private final EurekaRegistry registry;
    private final DiscoveryClient discoveryClient;

    private final AtomicReference<BridgeChannel> channelRef;

    @Inject
    public BridgeService(BridgeServerConfig config,
                         EurekaRegistry registry,
                         DiscoveryClient discoveryClient) {

        this.config = config;
        this.registry = registry;
        this.discoveryClient = discoveryClient;

        channelRef = new AtomicReference<>();
    }

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void connect() {
        logger.info("Starting bridge service");
        BridgeChannel channel = new BridgeChannel(registry, discoveryClient, config.getRefreshRateSec());
        if (channelRef.compareAndSet(null, channel)) {
            channel.connect();
        } else {
            logger.debug("No op, already connected");
        }
    }

    @PreDestroy
    public void close() {
        logger.info("Closing bridge service");
        BridgeChannel channel = channelRef.getAndSet(null);
        if (channel != null) {
            channel.close();
        }
    }
}
