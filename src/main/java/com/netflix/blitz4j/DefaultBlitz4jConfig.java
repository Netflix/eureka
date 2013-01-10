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

import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import com.netflix.config.DynamicPropertyFactory;

/**
 * The configuration class for blitz4j.
 * 
 * All custom configurations can be specified by properties as defined by the
 * configurations.
 * 
 * @author Karthik Ranganathan
 * 
 */
public class DefaultBlitz4jConfig implements BlitzConfig {

    private static final String GENERATE_LOG4J_LOCATION_INFO = "netflix.blitz4j.generateLog4jLocationInfo";

    private static final String BLITZ4J_ASYNC_APPENDERS = "blitz4j.asyncAppenders";

    private static final String GENERATE_BLITZ4J_LOCATIONINFO = "netflix.blitz4j.generateBlitz4jLocationInfo";

    private static final String PROP_ASYNC_APPENDERS = "log4j.logger.asyncAppenders";

    private static final String NETFLIX_BLITZ4J_PRINT_LOGGING_ERRORS = "netflix.blitz4j.printLoggingErrors";

    private static final String NETFLIX_BLITZ4J_LOCKFREE = "netflix.blitz4j.lockfree";
    // Use concurrent hash map to avoid multithreaded contention
    private Map<String, Object> propsMap = new ConcurrentHashMap<String, Object>();

    private Properties props;

    private static final DynamicPropertyFactory CONFIGURATION = DynamicPropertyFactory
            .getInstance();

    public DefaultBlitz4jConfig(Properties props) {
        this.props = props;
        if (this.props != null) {
            Enumeration enumeration = this.props.propertyNames();
            while (enumeration.hasMoreElements()) {
                String key = (String) enumeration.nextElement();
                String propertyValue = props.getProperty(key);
                this.propsMap.put(key, propertyValue);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#shouldUseLockFree()
     */
    @Override
    public boolean shouldUseLockFree() {
        return CONFIGURATION.getBooleanProperty(
                NETFLIX_BLITZ4J_LOCKFREE,
                Boolean.valueOf(this.getPropertyValue(NETFLIX_BLITZ4J_LOCKFREE,
                        "true"))).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#shouldPrintLoggingErrors()
     */
    @Override
    public boolean shouldPrintLoggingErrors() {
        return CONFIGURATION.getBooleanProperty(
                NETFLIX_BLITZ4J_PRINT_LOGGING_ERRORS,
                Boolean.valueOf(this.getPropertyValue(
                        NETFLIX_BLITZ4J_PRINT_LOGGING_ERRORS, "false"))).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#getAsyncAppenders()
     */
    @Override
    public String[] getAsyncAppenders() {
        return CONFIGURATION
                .getStringProperty(PROP_ASYNC_APPENDERS,
                        this.getPropertyValue(PROP_ASYNC_APPENDERS, "OFF"))
                .get().split(",");

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#getLogSummaryExpiry(java.lang.String)
     */
    @Override
    public int getLogSummaryExpiry(String originalAppenderName) {

        return CONFIGURATION.getIntProperty(
                "netflix.blitz4j." + originalAppenderName
                        + ".discardEntryExpireSeconds",
                Integer.valueOf(this.getPropertyValue("netflix.blitz4j."
                        + originalAppenderName + ".discardEntryExpireSeconds",
                        "60"))).get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#getLogSummarySize(java.lang.String)
     */
    @Override
    public int getLogSummarySize(String originalAppenderName) {

        return CONFIGURATION.getIntProperty(
                "netflix.blitz4j." + originalAppenderName + ".discardMapSize",
                Integer.valueOf(this.getPropertyValue("netflix.blitz4j."
                        + originalAppenderName + ".discardMapSize", "10000")))
                .get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#shouldGenerateBlitz4jLocationInfo()
     */
    @Override
    public boolean shouldGenerateBlitz4jLocationInfo() {
        return CONFIGURATION.getBooleanProperty(
                GENERATE_BLITZ4J_LOCATIONINFO,
                Boolean.valueOf(this.getPropertyValue(
                        GENERATE_BLITZ4J_LOCATIONINFO, "true"))).get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#shouldGenerateLog4jLocationInfo()
     */
    @Override
    public boolean shouldGenerateLog4jLocationInfo() {
        return CONFIGURATION.getBooleanProperty(
                GENERATE_LOG4J_LOCATION_INFO,
                Boolean.valueOf(this.getPropertyValue(
                        GENERATE_LOG4J_LOCATION_INFO, "false")))
                .get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#shouldSummarizeOverflow(java.lang.String)
     */
    @Override
    public boolean shouldSummarizeOverflow(String originalAppenderName) {
        return CONFIGURATION
                .getBooleanProperty(
                        "netflix.blitz4j." + originalAppenderName
                                + ".summarizeOverflow",
                        Boolean.valueOf(this.getPropertyValue(
                                "netflix.blitz4j." + originalAppenderName
                                        + ".summarizeOverflow", "true"))).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#getAsyncAppenderImplementationNames()
     */
    @Override
    public String[] getAsyncAppenderImplementationNames() {
        return CONFIGURATION
                .getStringProperty(
                        BLITZ4J_ASYNC_APPENDERS,
                        this.getPropertyValue(BLITZ4J_ASYNC_APPENDERS,
                                "com.netflix.blitz4j.AsyncAppender")).get()
                .split(",");
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#getBatcherQueueMaxMessages(java.lang.
     * String)
     */
    @Override
    public int getBatcherQueueMaxMessages(String batcherName) {
        return CONFIGURATION.getIntProperty(
                batcherName + "." + "queue.maxMessages",
                Integer.valueOf(this.getPropertyValue(batcherName + "."
                        + "queue.maxMessages", "10000"))).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#getBatchSize(java.lang.String)
     */
    @Override
    public int getBatchSize(String batcherName) {
        return CONFIGURATION.getIntProperty(
                batcherName + "." + "batch.maxMessages",
                Integer.valueOf(this.getPropertyValue(batcherName + "."
                        + "batch.maxMessages", "30"))).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#getBatcherWaitTimeBeforeShutdown(java
     * .lang.String)
     */
    @Override
    public int getBatcherWaitTimeBeforeShutdown(String batcherName) {
        return CONFIGURATION.getIntProperty(
                batcherName + ".waitTimeinMillis",
                Integer.valueOf(this.getPropertyValue(batcherName
                        + ".waitTimeinMillis", "10000"))).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.blitz4j.BlitzConfig#getBatcherMaxDelay(java.lang.String)
     */
    @Override
    public double getBatcherMaxDelay(String batcherName) {
        return CONFIGURATION.getDoubleProperty(
                batcherName + "." + "batch.maxDelay",
                Double.valueOf(this.getPropertyValue(batcherName
                        + ".waitTimeinMillis", "0.5"))).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#shouldWaitWhenBatcherQueueNotEmpty(java
     * .lang.String)
     */
    @Override
    public boolean shouldWaitWhenBatcherQueueNotEmpty(String batcherName) {
        return CONFIGURATION.getBooleanProperty(
                batcherName + ".blocking",
                Boolean.valueOf(this.getPropertyValue(
                        batcherName + ".blocking", "false"))).get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#getBatcherMinThreads(java.lang.String)
     */
    @Override
    public int getBatcherMinThreads(String batcherName) {
        return CONFIGURATION.getIntProperty(
                batcherName + ".minThreads",
                Integer.valueOf(this.getPropertyValue(batcherName
                        + ".minThreads", "1"))).get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#getBatcherMaxThreads(java.lang.String)
     */
    @Override
    public int getBatcherMaxThreads(String batcherName) {
        return CONFIGURATION.getIntProperty(
                batcherName + ".maxThreads",
                Integer.valueOf(this.getPropertyValue(batcherName
                        + ".maxThreads", "3"))).get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#getBatcherThreadKeepAliveTime(java.lang
     * .String)
     */
    @Override
    public int getBatcherThreadKeepAliveTime(String batcherName) {
        return CONFIGURATION.getIntProperty(
                batcherName + ".keepAliveTime",
                Integer.valueOf(this.getPropertyValue(batcherName
                        + ".keepAliveTime", "900"))).get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.blitz4j.BlitzConfig#shouldRejectWhenAllBatcherThreadsUsed
     * (java.lang.String)
     */
    @Override
    public boolean shouldRejectWhenAllBatcherThreadsUsed(String batcherName) {
        return CONFIGURATION.getBooleanProperty(
                batcherName + ".rejectWhenFull",
                Boolean.valueOf(this.getPropertyValue(batcherName
                        + ".rejectWhenFull", "false"))).get();
    }

    private String getPropertyValue(String key, String defaultValue) {
        String value = (String) propsMap.get(key);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    @Override
    public boolean shouldLoadLog4jPropertiesFromClassPath() {
        return CONFIGURATION.getBooleanProperty(
                "netflix.blitz4j" + ".shouldLoadLog4jProperties",
                Boolean.valueOf(this.getPropertyValue("netflix.blitz4j"
                        + ".shouldLoadLog4jProperties", "true"))).get();
    }
}
