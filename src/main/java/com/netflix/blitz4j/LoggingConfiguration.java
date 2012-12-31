/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.blitz4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.LogManager;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.Loader;
import org.apache.log4j.spi.LoggerFactory;
import org.slf4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.ExpandedConfigurationListenerAdapter;
import com.netflix.config.PropertyListener;
import com.netflix.logging.messaging.BatcherFactory;
import com.netflix.logging.messaging.MessageBatcher;

/**
 * The main configuration class that bootstraps the <em>blitz4j</em>
 * implementation.
 * 
 * <p>
 * The users can either use {@link #configure()} or
 * {@link #configure(Properties)} to kick start the configuration. If the
 * <code>log4j.configuration</code> is provided, the properties are additionally
 * loaded from the provided {@link URL}.
 * </p>
 * 
 * <p>
 * The list of appenders to be automatically converted can be provided by the
 * property <code>log4j.logger.asyncAppenders</code>. The configuration takes
 * these appenders and automatically enables them for asynchronous logging.
 * </p>
 * 
 * @author Karthik Ranganathan
 * 
 */
public class LoggingConfiguration implements PropertyListener {

    private static final String LOG4J_PROPERTIES = "log4j.properties";
    private static final String BLITZ_LOGGER_FACTORY = "com.netflix.blitz4j.NFCategoryFactory";
    private static final String PROP_LOG4J_CONFIGURATION = "log4j.configuration";
    private static final Object guard = new Object();
    private static final String PROP_LOG4J_LOGGER_FACTORY = "log4j.loggerFactory";
    private static final String LOG4J_FACTORY_IMPL = "com.netflix.logging.log4jAdapter.NFCategoryFactory";

    private static final String LOG4J_LOGGER_FACTORY = "log4j.loggerFactory";
    private static final String PROP_LOG4J_ORIGINAL_APPENDER_NAME = "originalAppenderName";
    private static final String LOG4J_PREFIX = "log4j.logger";
    private static final String LOG4J_APPENDER_DELIMITER = ".";
    private static final String LOG4J_APPENDER_PREFIX = "log4j.appender";
    private static final String ASYNC_APPENDERNAME_SUFFIX = "_ASYNC";
    private static final String ROOT_CATEGORY = "rootCategory";
    private static final String ROOT_LOGGER = "rootLogger";
    
    private Map<String, String> originalAsyncAppenderNameMap = new HashMap<String, String>();
    private BlitzConfig blitz4jConfig;
    private Properties props = new Properties();
    Properties updatedProps = new Properties();
    private final ExecutorService executorPool;
    private Logger logger;
    private static final int SLEEP_TIME_MS = 200;
    private static final CharSequence PROP_LOG4J_ASYNC_APPENDERS = "log4j.logger.asyncAppenders";

    private static LoggingConfiguration instance = new LoggingConfiguration();

    private LoggingConfiguration() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
        .setDaemon(false).setNameFormat("DynamicLog4jListener").build();

        this.executorPool = new ThreadPoolExecutor(0, 1, 15 * 60,
                TimeUnit.SECONDS, new SynchronousQueue(), threadFactory);
    }

    /**
     * Kick start the blitz4j implementation
     */
    public void configure() {
        this.configure(null);
    }

    /**
     * Kick start the blitz4j implementation.
     * 
     * @param props
     *            - The overriding <em>log4j</em> properties if any.
     */
    public void configure(Properties props) {
        this.originalAsyncAppenderNameMap.clear();
       // First try to load the log4j configuration file from the classpath
        String log4jConfigurationFile = System
        .getProperty(PROP_LOG4J_CONFIGURATION);

        if (log4jConfigurationFile != null) {
            loadLog4jConfigurationFile(log4jConfigurationFile);

        }
        else {
           InputStream in = null;
            try {
                URL url = Loader.getResource(LOG4J_PROPERTIES);
                if (url != null) {
                    in = url.openStream();
                    this.props.load(in);
                }
            } catch (Throwable t) {
                
            } finally {

                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignore) {

                    }
                }
            }
            
        }
        if (props != null) {
            Enumeration enumeration = props.propertyNames();
            while (enumeration.hasMoreElements()) {
                String key = (String) enumeration.nextElement();
                String propertyValue = props.getProperty(key);
                this.props.setProperty(key, propertyValue);
            }
        }
        this.blitz4jConfig = new DefaultBlitz4jConfig(this.props);
        
        NFHierarchy nfHierarchy = null;
        // Make log4j use blitz4j implementations
        if (blitz4jConfig.shouldUseLockFree()
                && (!NFHierarchy.class.equals(LogManager.getLoggerRepository()
                        .getClass()))) {
            nfHierarchy = new NFHierarchy(new NFRootLogger(
                    org.apache.log4j.Level.INFO));
            org.apache.log4j.LogManager.setRepositorySelector(
                    new NFRepositorySelector(nfHierarchy), guard);
        }
        String log4jLoggerFactory = System
        .getProperty(PROP_LOG4J_LOGGER_FACTORY);
        if (log4jLoggerFactory != null) {
            this.props.setProperty(PROP_LOG4J_LOGGER_FACTORY,
                    log4jLoggerFactory);
            if (nfHierarchy != null) {
                try {
                    LoggerFactory loggerFactory = (LoggerFactory) Class
                    .forName(log4jLoggerFactory).newInstance();
                    nfHierarchy.setLoggerFactory(loggerFactory);
                } catch (Throwable e) {
                    System.err
                    .println("Cannot set the logger factory. Hence reverting to default.");
                    e.printStackTrace();
                }
            }
        } else {
            if (blitz4jConfig.shouldUseLockFree()) {
                this.props.setProperty(PROP_LOG4J_LOGGER_FACTORY,
                        BLITZ_LOGGER_FACTORY);
            }
        }
    
        String[] asyncAppenderArray = blitz4jConfig.getAsyncAppenders();
        if (asyncAppenderArray == null) {
            return;
        }
        for (int i = 0; i < asyncAppenderArray.length; i++) {
            String oneAppenderName = asyncAppenderArray[i];
            if (i == 0) {
                continue;
            }
            String oneAsyncAppenderName = oneAppenderName
            + ASYNC_APPENDERNAME_SUFFIX;
            originalAsyncAppenderNameMap.put(oneAppenderName,
                    oneAsyncAppenderName);
        }
        try {
            convertConfiguredAppendersToAsync(this.props);
        } catch (Throwable e) {
            throw new RuntimeException("Could not configure async appenders ",
                    e);
        }
        // Yes second time init required as properties would have been during async appender conversion
        this.blitz4jConfig = new DefaultBlitz4jConfig(this.props);
        
        PropertyConfigurator.configure(this.props);
        closeNonexistingAsyncAppenders();
        this.logger = org.slf4j.LoggerFactory
        .getLogger(LoggingConfiguration.class);
        ConfigurationManager.getConfigInstance().addConfigurationListener(
                new ExpandedConfigurationListenerAdapter(this));
    }

    private void loadLog4jConfigurationFile(String log4jConfigurationFile) {
        InputStream in = null;
        try {
            URL url = new URL(log4jConfigurationFile);
            in = url.openStream();
            this.props.load(in);
        } catch (Throwable t) {
            throw new RuntimeException(
                    "Cannot load log4 configuration file specified in "
                    + PROP_LOG4J_CONFIGURATION, t);
        } finally {

            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignore) {

                }
            }
        }
    }

  

    public static LoggingConfiguration getInstance() {
        return instance;
    }

    public BlitzConfig getConfiguration() {
        return this.blitz4jConfig;
    }

    /**
     * Shuts down blitz4j cleanly by flushing out all the async related
     * messages.
     */
    public void stop() {
        MessageBatcher batcher = null;
        for (String originalAppenderName : originalAsyncAppenderNameMap
                .keySet()) {
            String batcherName = AsyncAppender.class.getName() + "."
            + originalAppenderName;
            batcher = BatcherFactory.getBatcher(batcherName);
            if (batcher == null) {
                continue;
            }
            batcher.stop();
        }
        for (String originalAppenderName : originalAsyncAppenderNameMap
                .keySet()) {
            String batcherName = AsyncAppender.class.getName() + "."
            + originalAppenderName;
            batcher = BatcherFactory.getBatcher(batcherName);
            if (batcher == null) {
                continue;
            }
            BatcherFactory.removeBatcher(batcherName);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.config.PropertyListener#addProperty(java.lang.Object,
     * java.lang.String, java.lang.Object, boolean)
     */
    public void addProperty(Object source, String name, Object value,
            boolean beforeUpdate) {
        if (shouldProcessProperty(name, beforeUpdate)) {
            updatedProps.put(name, value);
            reConfigureAsynchronously();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.config.PropertyListener#clear(java.lang.Object, boolean)
     */
    public void clear(Object source, boolean beforeUpdate) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.config.PropertyListener#clearProperty(java.lang.Object,
     * java.lang.String, java.lang.Object, boolean)
     */
    public void clearProperty(Object source, String name, Object value,
            boolean beforeUpdate) {
        if (shouldProcessProperty(name, beforeUpdate)) {
            updatedProps.remove(name);
            reConfigureAsynchronously();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.config.PropertyListener#configSourceLoaded(java.lang.Object)
     */
    public void configSourceLoaded(Object source) {
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.config.PropertyListener#setProperty(java.lang.Object,
     * java.lang.String, java.lang.Object, boolean)
     */
    public void setProperty(Object source, String name, Object value,
            boolean beforeUpdate) {
        if (shouldProcessProperty(name, beforeUpdate)) {
            updatedProps.put(name, value);
            reConfigureAsynchronously();
        }
    }

    /**
     * Reconfigure log4j at run-time.
     * 
     * @param name
     *            - The name of the property that changed
     * @param value
     *            - The new value of the property
     * @throws FileNotFoundException
     * @throws ConfigurationException
     */
    private void reConfigure() throws ConfigurationException,
    FileNotFoundException {

        Properties consolidatedProps = new Properties();
        consolidatedProps.putAll(props);
        logger.info("Updated properties is :" + updatedProps);
        consolidatedProps.putAll(updatedProps);
        logger.info("The root category for log4j.rootCategory now is "
                + consolidatedProps.getProperty("log4j.rootCategory"));
        logger.info("The root category for log4j.rootLogger now is "
                + consolidatedProps.getProperty("log4j.rootLogger"));

        // Pause the async appenders so that the appenders are not accessed
        for (String originalAppenderName : originalAsyncAppenderNameMap
                .keySet()) {
            MessageBatcher asyncBatcher = BatcherFactory
            .getBatcher(AsyncAppender.class.getName() + "."
                    + originalAppenderName);
            if (asyncBatcher == null) {
                continue;
            }
            asyncBatcher.pause();
        }

        // Configure log4j using the new set of properties
        configureLog4j(consolidatedProps);
        // Resume all the batchers to continue logging
        for (String originalAppenderName : originalAsyncAppenderNameMap
                .keySet()) {
            MessageBatcher asyncBatcher = BatcherFactory
            .getBatcher(AsyncAppender.class.getName() + "."
                    + originalAppenderName);
            if (asyncBatcher == null) {
                continue;
            }
            asyncBatcher.resume();
        }
    }

    /**
     * Configure log4j with the given properties.
     * 
     * @param props
     *            The properties that needs to be configured for log4j
     * @throws ConfigurationException
     * @throws FileNotFoundException
     */
    private void configureLog4j(Properties props)
    throws ConfigurationException, FileNotFoundException {
        if (blitz4jConfig.shouldUseLockFree()
                && (props.getProperty(LOG4J_LOGGER_FACTORY) == null)) {
            props.setProperty(LOG4J_LOGGER_FACTORY, LOG4J_FACTORY_IMPL);
        }
        convertConfiguredAppendersToAsync(props);
        logger.info("Configuring log4j with properties :" + props);
        PropertyConfigurator.configure(props);
    }

    /**
     * Asynchronously reconfigure <code>log4j</code>. This make sure there is
     * only one configuration currently in progress and rejects multiple
     * reconfigurations at the same time there by causing logging contentions.
     */
    private void reConfigureAsynchronously() {
        try {
            executorPool.submit(new Runnable() {

                public void run() {
                    try {
                        Thread.sleep(SLEEP_TIME_MS);
                        logger.info("Configuring log4j dynamically");
                        reConfigure();
                    } catch (Throwable th) {
                        logger.error("Cannot dynamically configure log4j :", th);
                    }
                }
            });
        } catch (RejectedExecutionException re) {
            throw re;
        }
    }

    /**
     * Check if the property that is being changed is something that this
     * configuration cares about.
     * 
     * The implementation only cares about changes related to <code>log4j</code>
     * properties.
     * 
     * @param name
     *            -The name of the property which should be checked.
     * @param beforeUpdate
     *            -true, if this call is made before the property has been
     *            updated, false otherwise.
     * @return
     */
    private boolean shouldProcessProperty(String name, boolean beforeUpdate) {
        if (name == null) {
            logger.warn("The listener got a null value for name");
            return false;
        }
        if (beforeUpdate) {
            return false;
        }
        return name.startsWith(LOG4J_PREFIX);
    }

    /**
     * Convert appenders specified by the property
     * <code>log4j.logger.asyncAppender</code> to the blitz4j Asynchronous
     * appenders.
     * 
     * @param props
     *            - The properties that need to be passed into the log4j for
     *            configuration.
     * @throws ConfigurationException
     * @throws FileNotFoundException
     */
    private void convertConfiguredAppendersToAsync(Properties props)
            throws ConfigurationException, FileNotFoundException {
        for (Map.Entry<String, String> originalAsyncAppenderMapEntry : originalAsyncAppenderNameMap
                .entrySet()) {
            String asyncAppenderName = originalAsyncAppenderMapEntry.getValue();
            props.setProperty(LOG4J_APPENDER_PREFIX + LOG4J_APPENDER_DELIMITER
                    + asyncAppenderName, AsyncAppender.class.getName());
            // Set the original appender so that it can be fetched later after
            // configuration
            String originalAppenderName = originalAsyncAppenderMapEntry
                    .getKey();
            props.setProperty(LOG4J_APPENDER_PREFIX + LOG4J_APPENDER_DELIMITER
                    + asyncAppenderName + LOG4J_APPENDER_DELIMITER
                    + PROP_LOG4J_ORIGINAL_APPENDER_NAME, originalAppenderName);
            // Set the batcher to reject the collector request instead of it
            // participating in processing
            this.props.setProperty(
                    "batcher." + AsyncAppender.class.getName() + "."
                            + originalAppenderName + "." + "rejectWhenFull",
                    "true");
            // Set the default value of the processing max threads to 1, if a
            // value is not specified
            String maxThreads = this.props.getProperty (
                    "batcher." + AsyncAppender.class.getName() + "."
                            + originalAppenderName + "." + "maxThreads");
            if (maxThreads == null) {
                this.props.setProperty(
                        "batcher." + AsyncAppender.class.getName() + "."
                                + originalAppenderName + "." + "maxThreads",
                        "1");
            }

            for (Map.Entry mapEntry : props.entrySet()) {
                String key = mapEntry.getKey().toString();
                if ((key.contains(LOG4J_PREFIX) || key.contains(ROOT_CATEGORY) || key
                        .contains(ROOT_LOGGER))
                        && !key.contains(PROP_LOG4J_ASYNC_APPENDERS)
                        && !key.contains(PROP_LOG4J_ORIGINAL_APPENDER_NAME)) {
                    Object value = mapEntry.getValue();
                    if (value != null) {
                        String[] values = (String.class.cast(value)).split(",");
                        String valueString = "";
                        int ctr = 0;
                        for (String oneValue : values) {
                            ++ctr;
                            if (originalAppenderName.equals(oneValue)) {
                                oneValue = asyncAppenderName;
                            }
                            if (ctr != values.length) {
                                valueString = valueString + oneValue + ",";
                            } else {
                                valueString = valueString + oneValue;
                            }
                        }
                        mapEntry.setValue(valueString);
                    }
                }
            }
        }
    }

    /**
     * Closes any asynchronous appenders that were not removed during configuration.
     */
    private void closeNonexistingAsyncAppenders() {
        org.apache.log4j.Logger rootLogger = LogManager.getRootLogger();
        if (NFLockFreeLogger.class.isInstance(rootLogger)) {
            ((NFLockFreeLogger)rootLogger).reconcileAppenders();
        }
        Enumeration enums = LogManager.getCurrentLoggers();
        while (enums.hasMoreElements()) {
            Object myLogger = enums.nextElement();
            if (NFLockFreeLogger.class.isInstance(myLogger)) {
                ((NFLockFreeLogger)myLogger).reconcileAppenders();
            }
        }
    }

}
