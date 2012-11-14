package com.netflix.blitz4j;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.event.ConfigurationEvent;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.spi.LoggerFactory;
import org.slf4j.Logger;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;
import com.netflix.config.ExpandedConfigurationListenerAdapter;
import com.netflix.config.PropertyListener;
import com.netflix.logging.messaging.BatcherFactory;
import com.netflix.logging.messaging.MessageBatcher;

public class LoggingConfiguration implements PropertyListener {

    private static final String PROP_LOG4J_CONFIGURATION = "log4j.configuration";
    private static final Object guard = new Object();
    private static final String PROP_LOG4J_LOGGER_FACTORY = "log4j.loggerFactory";
    private static final String LOG4J_FACTORY_IMPL = "com.netflix.logging.log4jAdapter.NFCategoryFactory";

    private static final String LOG4J_LOGGER_FACTORY = "log4j.loggerFactory";

    private static final DynamicBooleanProperty lockFree = DynamicPropertyFactory
            .getInstance().getBooleanProperty("netflix.blitz4j.lockfree", true);

    private static final String PROP_LOG4J_ORIGINAL_APPENDER_NAME = "originalAppenderName";
    private static final String LOGGER_ASYNC_APPENDER = "asyncAppenders";
    private static final String PROP_LOG4J_ASYNC_APPENDERS = "log4j.logger."
            + LOGGER_ASYNC_APPENDER;
    private static final String LOG4J_PREFIX = "log4j.logger";
    private static final String LOG4J_APPENDER_DELIMITER = ".";
    private static final String LOG4J_APPENDER_PREFIX = "log4j.appender";
    private static final String ASYNC_APPENDERNAME_SUFFIX = "_ASYNC";
    private static final String CONFIGURABLE_ASYNC_APPENDERNAME_LIST_DELIMITER = ",";
    private static final String ROOT_CATEGORY = "rootCategory";
    private static final String ROOT_LOGGER = "rootLogger";

    private static final DynamicStringProperty CONFIGURABLE_ASYNC_APPENDERNAME_LIST = DynamicPropertyFactory
            .getInstance().getStringProperty(PROP_LOG4J_ASYNC_APPENDERS,
                    "INFO,CONSOLE");
    private Map<String, String> originalAsyncAppenderNameMap = new HashMap<String, String>();
    private Properties props = new Properties();
    Properties updatedProps = new Properties();
    private final ExecutorService executorPool;
    private Logger logger;
    private static final int SLEEP_TIME_MS = 200;

    private static LoggingConfiguration instance = new LoggingConfiguration();

    private LoggingConfiguration() {
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setDaemon(false).setNameFormat("DynamicLog4jListener").build();

        this.executorPool = new ThreadPoolExecutor(0, 1, 15 * 60,
                TimeUnit.SECONDS, new SynchronousQueue(), threadFactory);
    }

    /**
     * Configure log4j with some default settings to control any extra logging
     * by apache. This will control commons-logging calls only if there is no
     * other commons-logging.properties apart from the one in the platform.To be
     * explicit about using log4j, a system property
     * org.apache.commons.logging.Log
     * =org.apache.commons.logging.impl.Log4JLogger should be passed in before
     * commons-logging initialization (which happens with the first
     * commons-logging call)
     */
    public void configure(Properties props) {
        this.props = props;
        NFHierarchy nfHierarchy = null;
        if (lockFree.get()) {
            nfHierarchy = new NFHierarchy(new NFRootLogger(
                    org.apache.log4j.Level.INFO));
            org.apache.log4j.LogManager.setRepositorySelector(
                    new NFRepositorySelector(nfHierarchy), guard);
        }
        String log4jLoggerFactory = System
                .getProperty(PROP_LOG4J_LOGGER_FACTORY);
        if (log4jLoggerFactory != null) {
            // This is needed for straight log4j calls which does not go
            // through the NFLogger
            props.setProperty(PROP_LOG4J_LOGGER_FACTORY, log4jLoggerFactory);
            // This is needed for the NFHierarchy
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
            if (lockFree.get()) {
                props.setProperty(PROP_LOG4J_LOGGER_FACTORY,
                        "com.netflix.blitz4j.NFCategoryFactory");
            }
        }
        String log4jConfigurationFile = System
                .getProperty(PROP_LOG4J_CONFIGURATION);

        if (log4jConfigurationFile != null) {
            InputStream in = null;
            try {
                URL url = new URL(log4jConfigurationFile);
                in = url.openStream();
                props.load(in);
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
        String asyncAppenderList = CONFIGURABLE_ASYNC_APPENDERNAME_LIST.get();
        String[] asyncAppenderArray = asyncAppenderList
                .split(CONFIGURABLE_ASYNC_APPENDERNAME_LIST_DELIMITER);
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
            convertConfiguredAppendersToAsync(props);
        } catch (Throwable e) {
            throw new RuntimeException("Could not configure async appenders ",
                    e);
        }
        PropertyConfigurator.configure(props);
        this.logger = org.slf4j.LoggerFactory
                .getLogger(LoggingConfiguration.class);
        ConfigurationManager.getConfigInstance().addConfigurationListener(
                new ExpandedConfigurationListenerAdapter(this));
    }

    public static LoggingConfiguration getInstance() {
        return instance;
    }

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
            ((Configuration) DynamicPropertyFactory
                    .getBackingConfigurationSource()).setProperty("batcher."
                    + AsyncAppender.class.getName() + "."
                    + originalAppenderName + "."
                    + "threadPoolDefaultRejectionHandler", true);
            for (Map.Entry mapEntry : props.entrySet()) {
                String key = mapEntry.getKey().toString();
                if ((key.contains(LOG4J_PREFIX) || key.contains(ROOT_CATEGORY) || key
                        .contains(ROOT_LOGGER))
                        && !key.contains(PROP_LOG4J_ASYNC_APPENDERS)
                        && !key.contains(PROP_LOG4J_ORIGINAL_APPENDER_NAME)) {
                    Object value = mapEntry.getValue();
                    if (value != null && !((String)value).contains(asyncAppenderName)) {
                    String convertedString = value.toString().replace(
                            originalAppenderName, asyncAppenderName);
                    mapEntry.setValue(convertedString);
                    }
                    
                }
            }
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
        // Pause the logging batchers so that tracers and counters are not sent
        // during reconfiguration
        String batcherName = "com.netflix.logging";
        MessageBatcher loggingBatcher = BatcherFactory.getBatcher(batcherName);
        if (loggingBatcher != null) {
            loggingBatcher.pause();
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
        if (loggingBatcher != null) {
            loggingBatcher.resume();
        }
    }

    private void configureLog4j(Properties props)
            throws ConfigurationException, FileNotFoundException {
        if (lockFree.get() && (props.getProperty(LOG4J_LOGGER_FACTORY) == null)) {
            props.setProperty(LOG4J_LOGGER_FACTORY, LOG4J_FACTORY_IMPL);
        }
        convertConfiguredAppendersToAsync(props);
        logger.info("Configuring log4j with properties :" + props);
        PropertyConfigurator.configure(props);
    }

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
     * Handle log4j property additions (non-Javadoc)
     * 
     * @see
     * com.netflix.config.NetflixConfigurationListener#addProperty(java.lang
     * .Object, java.lang.String, java.lang.Object, boolean)
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
     * @see
     * com.netflix.config.NetflixConfigurationListener#clear(java.lang.Object,
     * boolean)
     */
    public void clear(Object source, boolean beforeUpdate) {
    }

    /*
     * Handle removal of log4j properties (non-Javadoc)
     * 
     * @see
     * com.netflix.config.NetflixConfigurationListener#clearProperty(java.lang
     * .Object, java.lang.String, java.lang.Object, boolean)
     */
    public void clearProperty(Object source, String name, Object value,
            boolean beforeUpdate) {
        if (shouldProcessProperty(name, beforeUpdate)) {
            updatedProps.remove(name);
            reConfigureAsynchronously();
        }
    }

    public void configSourceLoaded(Object source) {
    }

    /*
     * Handles update of log4j properties (non-Javadoc)
     * 
     * @see
     * com.netflix.config.NetflixConfigurationListener#setProperty(java.lang
     * .Object, java.lang.String, java.lang.Object, boolean)
     */
    public void setProperty(Object source, String name, Object value,
            boolean beforeUpdate) {
        if (shouldProcessProperty(name, beforeUpdate)) {
            updatedProps.put(name, value);
            reConfigureAsynchronously();
        }
    }

    public void configurationChanged(ConfigurationEvent arg0) {
    }

    private void reConfigureAsynchronously() {
        try {
            executorPool.submit(new Runnable() {

                public void run() {
                    try {
                        Thread.sleep(SLEEP_TIME_MS);
                        logger.info("Configuring log4j dynamically");
                        reConfigure();
                    } catch (Throwable th) {
                        logger.error("Cannot dynamically configure log4j :");
                        th.printStackTrace();
                    }
                }
            });
        } catch (RejectedExecutionException re) {
            throw re;
        }
    }

    private boolean shouldProcessProperty(String name, boolean beforeUpdate) {
        if (name == null) {
            logger.warn("The listener got a null value for name");
            return false;
        }
        if (beforeUpdate) {
            return false;
        }
        return name.startsWith("log4j") && (!name.contains(ROOT_LOGGER))
                && (!name.contains(ROOT_CATEGORY));
    }
}
