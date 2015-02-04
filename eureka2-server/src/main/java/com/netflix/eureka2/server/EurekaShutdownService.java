package com.netflix.eureka2.server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Injector;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.service.SelfRegistrationService;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import com.netflix.governator.lifecycle.LifecycleManager;
import netflix.karyon.ShutdownListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaShutdownService {

    private static final Logger logger = LoggerFactory.getLogger(EurekaShutdownService.class);

    private final int port;
    private final LifecycleManager lifecycleManager;
    private final SelfRegistrationService selfRegistrationService;

    private ShutdownListener shutdownListener;

    @Inject
    public EurekaShutdownService(EurekaCommonConfig config, Injector injector) {
        this.lifecycleManager = injector.getInstance(LifecycleManager.class);
        this.selfRegistrationService = injector.getInstance(SelfRegistrationService.class);
        this.port = config.getShutDownPort();
    }

    public int getShutdownPort() {
        return shutdownListener.getShutdownPort();
    }

    @PostConstruct
    public void start() {

        shutdownListener = new ShutdownListener(port, new Action0() {
            @Override
            public void call() {
                logger.info("Eureka server shutdown requested.");

                logger.info("Unregistering itself from the registry...");
                selfRegistrationService.shutdown();

                logger.info("Shutting down service container...");
                lifecycleManager.close();
            }
        });
        shutdownListener.start();

        selfRegistrationService.resolve()
                .take(1)
                .doOnNext(new Action1<InstanceInfo>() {
                    @Override
                    public void call(InstanceInfo instanceInfo) {
                        logger.info("Instance {} listening for shutdown on port {}", instanceInfo.getId(), getShutdownPort());
                    }
                }).subscribe(new NoOpSubscriber<>());  // logging only, so ignore errors by using a no-op subscriber
    }

    @PreDestroy
    public void stop() {
        if (shutdownListener != null) {
            try {
                shutdownListener.shutdown();
            } catch (IllegalStateException e) {
                // If shutdown was triggered by the shutdown listener, it terminates the server itself.
                // In such case we will get  this exception, which we can safely ignore.
            } catch (InterruptedException e) {
                logger.info("Shutdown process interrupted", e);
            } finally {
                shutdownListener = null;
            }
        }
    }
}
