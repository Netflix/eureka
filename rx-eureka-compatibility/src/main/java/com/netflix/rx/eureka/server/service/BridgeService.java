package com.netflix.rx.eureka.server.service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistry;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.server.BridgeServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author David Liu
 */
@Singleton
public class BridgeService {
    private static final Logger logger = LoggerFactory.getLogger(BridgeService.class);

    private final BridgeServerConfig config;
    private final EurekaServerRegistry<InstanceInfo> registry;
    private final DiscoveryClient discoveryClient;

    private final AtomicReference<BridgeChannel> channelRef;

    @Inject
    public BridgeService(BridgeServerConfig config,
                         EurekaServerRegistry registry,
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
