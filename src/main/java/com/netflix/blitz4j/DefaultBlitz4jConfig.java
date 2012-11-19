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

    private static final DynamicPropertyFactory CONFIGURATION = DynamicPropertyFactory.getInstance();

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#shouldUseLockFree()
     */
    @Override
    public boolean shouldUseLockFree() {
        return CONFIGURATION
                .getBooleanProperty("netflix.blitz4j.lockfree", true).get();
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#shouldPrintLoggingErrors()
     */
    @Override
    public boolean shouldPrintLoggingErrors() {
        return CONFIGURATION
                .getBooleanProperty("netflix.blitz4j.printLoggingErrors", false)
                .get();
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getAsyncAppenders()
     */
    @Override
    public String[] getAsyncAppenders() {
        return CONFIGURATION
                .getStringProperty("log4j.logger.asyncAppenders", "OFF").get()
                .split(",");

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getLogSummaryExpiry(java.lang.String)
     */
    @Override
    public int getLogSummaryExpiry(String originalAppenderName) {

        return CONFIGURATION
                .getIntProperty(
                        "netflix.blitz4j." + originalAppenderName
                                + ".discardEntryExpireSeconds", 60).get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getLogSummarySize(java.lang.String)
     */
    @Override
    public int getLogSummarySize(String originalAppenderName) {

        return CONFIGURATION
                .getIntProperty(
                        "netflix.blitz4j." + originalAppenderName
                                + ".discardMapSize", 10000).get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#shouldGenerateBlitz4jLocationInfo()
     */
    @Override
    public boolean shouldGenerateBlitz4jLocationInfo() {
        return CONFIGURATION
                .getBooleanProperty(
                        "netflix.blitz4j.generateBlitz4jLocationInfo", true)
                .get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#shouldGenerateLog4jLocationInfo()
     */
    @Override
    public boolean shouldGenerateLog4jLocationInfo() {
        return CONFIGURATION
                .getBooleanProperty(
                        "netflix.blitz4j.generateLog4jLocationInfo", false)
                .get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#shouldSummarizeOverflow(java.lang.String)
     */
    @Override
    public boolean shouldSummarizeOverflow(String originalAppenderName) {
        return CONFIGURATION
                .getBooleanProperty(
                        "netflix.blitz4j" + originalAppenderName
                                + ".summarizeOverflow", true).get();
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getAsyncAppenderImplementationNames()
     */
    @Override
    public String[] getAsyncAppenderImplementationNames() {
        return CONFIGURATION
                .getStringProperty("blitz4j.asyncAppenders",
                        "com.netflix.blitz4j.AsyncAppender").get().split(",");
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getBatcherQueueMaxMessages(java.lang.String)
     */
    @Override
    public int getBatcherQueueMaxMessages(String batcherName) {
        return CONFIGURATION
                .getIntProperty(batcherName + "." + "queue.maxMessages", 10000)
                .get();
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getBatchSize(java.lang.String)
     */
    @Override
    public int getBatchSize(String batcherName) {
        return CONFIGURATION
                .getIntProperty(batcherName + "." + "batch.maxMessages", 30)
                .get();
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getBatcherWaitTimeBeforeShutdown(java.lang.String)
     */
    @Override
    public int getBatcherWaitTimeBeforeShutdown(String batcherName) {
        return CONFIGURATION
                .getIntProperty(batcherName + ".waitTimeinMillis", 10000).get();
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getBatcherMaxDelay(java.lang.String)
     */
    @Override
    public double getBatcherMaxDelay(String batcherName) {
        return CONFIGURATION
                .getDoubleProperty(batcherName + "." + "batch.maxDelay", 0.5)
                .get();
    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#shouldWaitWhenBatcherQueueNotEmpty(java.lang.String)
     */
    @Override
    public boolean shouldWaitWhenBatcherQueueNotEmpty(String batcherName) {
        return CONFIGURATION
                .getBooleanProperty(batcherName + ".blocking", false).get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getBatcherMinThreads(java.lang.String)
     */
    @Override
    public int getBatcherMinThreads(String batcherName) {
        return CONFIGURATION
                .getIntProperty(batcherName + ".minThreads", 1).get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getBatcherMaxThreads(java.lang.String)
     */
    @Override
    public int getBatcherMaxThreads(String batcherName) {
        return CONFIGURATION
                .getIntProperty(batcherName + ".maxThreads", 3).get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#getBatcherThreadKeepAliveTime(java.lang.String)
     */
    @Override
    public int getBatcherThreadKeepAliveTime(String batcherName) {
        return CONFIGURATION
                .getIntProperty(batcherName + ".keepAliveTime", 900).get();

    }

    /* (non-Javadoc)
     * @see com.netflix.blitz4j.BlitzConfig#shouldRejectWhenAllBatcherThreadsUsed(java.lang.String)
     */
    @Override
    public boolean shouldRejectWhenAllBatcherThreadsUsed(String batcherName) {
        return CONFIGURATION
                .getBooleanProperty(batcherName + ".rejectWhenFull", false)
                .get();
    }
}
