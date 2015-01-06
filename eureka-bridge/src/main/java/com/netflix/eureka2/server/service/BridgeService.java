package com.netflix.eureka2.server.service;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.server.config.BridgeServerConfig;
import com.netflix.eureka2.server.metric.BridgeServerMetricFactory;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.channel.BridgeChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;

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
    private final SelfInfoResolver selfIdentityService;
    private final BridgeServerMetricFactory metricFactory;
    private final SourcedEurekaRegistry<InstanceInfo> registry;
    private final DiscoveryClient discoveryClient;

    private final AtomicReference<BridgeChannel> channelRef;

    @Inject
    public BridgeService(BridgeServerConfig config,
                         SelfInfoResolver selfIdentityService,
                         BridgeServerMetricFactory metricFactory,
                         SourcedEurekaRegistry registry,
                         DiscoveryClient discoveryClient) {

        this.config = config;
        this.selfIdentityService = selfIdentityService;
        this.metricFactory = metricFactory;
        this.registry = registry;
        this.discoveryClient = discoveryClient;

        channelRef = new AtomicReference<>();
    }

    @SuppressWarnings("unchecked")
    @PostConstruct
    public void connect() {
        selfIdentityService.resolve().subscribe(new Subscriber<InstanceInfo>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
                logger.error("failed to connect bridge channel, cannot resolve self instanceInfo", e);
            }

            @Override
            public void onNext(InstanceInfo self) {
                logger.info("Starting bridge service");
                BridgeChannel channel = new BridgeChannel(
                        registry,
                        discoveryClient,
                        config.getRefreshRateSec(),
                        self,
                        metricFactory.getBridgeChannelMetrics());
                if (channelRef.compareAndSet(null, channel)) {
                    channel.connect();
                } else {
                    logger.debug("No op, already connected");
                }
            }
        });
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
