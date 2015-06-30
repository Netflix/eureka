package com.netflix.eureka2.server;

import com.netflix.governator.LifecycleInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaServerRunner<S extends AbstractEurekaServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaServerRunner.class);

    private final Class<S> serverClass;
    protected final String name;

    protected LifecycleInjector injector;

    protected EurekaServerRunner(Class<S> serverClass) {
        this.name = null;
        this.serverClass = serverClass;
    }

    protected EurekaServerRunner(String name, Class<S> serverClass) {
        this.name = name;
        this.serverClass = serverClass;
    }

    public S getEurekaServer() {
        return injector.getInstance(serverClass);
    }

    public boolean start() {
        try {
            injector = createInjector();
        } catch (Exception e) {
            logger.error("Error while starting Eureka Write server.", e);
            return false;
        }
        logger.info("Container {} started", getClass().getSimpleName());
        return true;
    }

    public void shutdown() {
        if (injector != null) {
            injector.shutdown();
        }
    }

    public void awaitTermination() {
        try {
            injector.awaitTermination();
        } catch (InterruptedException e) {
            logger.info("Container {} shutting down", getClass().getSimpleName());
        }
    }

    protected abstract LifecycleInjector createInjector();
}
