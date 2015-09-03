package com.netflix.eureka2.server.service.bootstrap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.health.AbstractHealthStatusProvider;
import com.netflix.eureka2.health.SubsystemDescriptor;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.Source.Origin;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.server.config.BootstrapConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;
import rx.Subscription;

/**
 * @author Tomasz Bak
 */
@Singleton
public class RegistryBootstrapCoordinator extends AbstractHealthStatusProvider<RegistryBootstrapCoordinator> {

    private static final Logger logger = LoggerFactory.getLogger(RegistryBootstrapCoordinator.class);

    private static final SubsystemDescriptor<RegistryBootstrapCoordinator> DESCRIPTOR = new SubsystemDescriptor<>(
            RegistryBootstrapCoordinator.class,
            "Write Server registry bootstrap",
            "Bootstrap registry loader from external source"
    );

    private final BootstrapConfig config;
    private final RegistryBootstrapService registryBootstrapService;
    private final EurekaRegistry<InstanceInfo> registry;

    private Subscription bootstrapSubscription;

    @Inject
    public RegistryBootstrapCoordinator(BootstrapConfig config,
                                        RegistryBootstrapService registryBootstrapService,
                                        EurekaRegistry registry) {
        super(Status.STARTING, DESCRIPTOR);
        this.config = config;
        this.registryBootstrapService = registryBootstrapService;
        this.registry = registry;
    }

    @PostConstruct
    public void bootstrap() {
        if (!config.isBootstrapEnabled()) {
            moveHealthTo(Status.UP);
            logger.info("Registry bootstrap disabled. Server will start with an empty registry.");
            return;
        }

        final Source source = new Source(Origin.BOOTSTRAP, "loaded@" + System.currentTimeMillis());

        logger.info("Starting registry bootstrapping using {}...", registryBootstrapService.getClass().getName());
        bootstrapSubscription = registryBootstrapService.loadIntoRegistry(registry, source)
                .timeout(config.getBootstrapTimeoutMs(), TimeUnit.MILLISECONDS)
                .subscribe(
                        new Subscriber<Void>() {
                            @Override
                            public void onCompleted() {
                                logger.info("Registry uploaded. Changing bootstrap health status to UP");
                                moveHealthTo(Status.UP);
                            }

                            @Override
                            public void onError(Throwable e) {
                                logger.error("Couldn't bootstrap registry from external source." +
                                        "Server starts with empty registry. Changing bootstrap health status to UP", e);
                                moveHealthTo(Status.UP);
                            }

                            @Override
                            public void onNext(Void aVoid) {
                                // no-op
                            }
                        }
                );
    }

    @PreDestroy
    public void shutdown() {
        if (bootstrapSubscription != null) {
            bootstrapSubscription.unsubscribe(); // In case shutdown happens before registry bootstrap has a chance to finish
        }
    }
}
