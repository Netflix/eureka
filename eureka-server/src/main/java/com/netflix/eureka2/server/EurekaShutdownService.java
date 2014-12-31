package com.netflix.eureka2.server;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.config.EurekaServerConfig;
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

    private ShutdownListener shutdownListener;

    @Inject
    public EurekaShutdownService(EurekaServerConfig config) {
        this.port = config.getShutDownPort();
    }

    @PostConstruct
    public void start() {
        shutdownListener = new ShutdownListener(port, new Action0() {
            @Override
            public void call() {
                logger.info("Eureka server shutdown requested.");
            }
        });
        shutdownListener.start();
    }

    @PreDestroy
    public void stop() {
        if (shutdownListener != null) {
            try {
                shutdownListener.shutdown();
            } catch (InterruptedException e) {
                logger.info("Shutdown process interrupted", e);
            } finally {
                shutdownListener = null;
            }
        }
    }

}
