package com.netflix.eureka2.server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.governator.lifecycle.LifecycleManager;
import netflix.karyon.ShutdownListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.functions.Action0;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EurekaShutdownService {

    private static final Logger logger = LoggerFactory.getLogger(EurekaShutdownService.class);

    private final int port;
    private final LifecycleManager lifecycleManager;

    private ShutdownListener shutdownListener;

    @Inject
    public EurekaShutdownService(EurekaServerConfig config, LifecycleManager lifecycleManager) {
        this.lifecycleManager = lifecycleManager;
        this.port = config.getShutDownPort();
    }

    @PostConstruct
    public void start() {
        shutdownListener = new ShutdownListener(port, new Action0() {
            @Override
            public void call() {
                logger.info("Eureka server shutdown requested.");
                lifecycleManager.close();
            }
        });
        shutdownListener.start();
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
