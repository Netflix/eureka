package com.netflix.eureka2.server;

import java.util.Arrays;

import com.netflix.eureka2.server.config.EurekaCommandLineParser;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.governator.LifecycleInjector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaServerRunner<C extends EurekaCommonConfig, S extends AbstractEurekaServer> {

    private static final Logger logger = LoggerFactory.getLogger(EurekaServerRunner.class);

    private final Class<S> serverClass;
    protected final String name;
    private final String[] args;

    protected C config;

    protected LifecycleInjector injector;

    protected EurekaServerRunner(String[] args, Class<S> serverClass) {
        this.name = null;
        this.args = args;
        this.serverClass = serverClass;
    }

    protected EurekaServerRunner(C config, Class<S> serverClass) {
        this.name = null;
        this.args = null;
        this.config = config;
        this.serverClass = serverClass;
    }

    protected EurekaServerRunner(String name, Class<S> serverClass) {
        this.name = name;
        this.args = null;
        this.config = null;
        this.serverClass = serverClass;
    }

    public S getEurekaServer() {
        return injector.getInstance(serverClass);
    }

    public boolean start() {
        if (config != null && args != null) {
            if (!parseCliConfiguration()) {
                return false;
            }
        }
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

    protected abstract EurekaCommandLineParser newCommandLineParser(String[] args);

    private boolean parseCliConfiguration() {
        if (args.length == 0) {
            logger.info("No command line parameters provided; enabling archaius property loader for server bootstrapping");
            return true;
        }

        EurekaCommandLineParser commandLineParser = newCommandLineParser(args);
        try {
            config = (C) commandLineParser.process();
        } catch (Exception e) {
            System.err.println("ERROR: invalid configuration parameters; " + e.getMessage());
            return false;
        }

        if (commandLineParser.hasHelpOption()) {
            commandLineParser.printHelp();
            return false;
        }

        logger.info("Server bootstrapping from command line parameters {}", Arrays.toString(args));
        return true;
    }
}
