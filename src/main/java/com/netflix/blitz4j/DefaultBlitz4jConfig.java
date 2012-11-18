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
public class DefaultBlitz4jConfig {

    /**
     * Indicates whether blitz4j should use its less contended implementation.
     * 
     * @return
     */
    public boolean shouldUseLockFree() {
        return DynamicPropertyFactory.getInstance()
                .getBooleanProperty("netflix.blitz4j.lockfree", true).get();
    }

    /**
     * Indicates whether blitz4j should print the errors during logging for
     * debugging.
     * 
     * @return
     */
    public boolean shouldPrintLoggingErrors() {
        return DynamicPropertyFactory
                .getInstance()
                .getBooleanProperty("netflix.blitz4j.printLoggingErrors", false)
                .get();
    }

    /**
     * Get the list of asynchronous appenders. The configuration is specified similar to any log4j loging override
     * (ie)log4j.logger.asyncAppenders=INFO,MYAPPENDER. The logging level in this definitions bears no specific significance
     * and is only for completion.
     * 
     * @return
     */
    public String[] getAsyncAppenders() {
        return DynamicPropertyFactory.getInstance()
                .getStringProperty("log4j.logger.asyncAppenders", "OFF").get()
                .split(",");

    }

    public int getDiscardEntryExpireSeconds(String originalAppenderName) {

        return DynamicPropertyFactory
                .getInstance()
                .getIntProperty(
                        "netflix.blitz4j." + originalAppenderName
                                + ".discardEntryExpireSeconds", 60).get();

    }

    public int getDiscardMapSize(String originalAppenderName) {

        return DynamicPropertyFactory
                .getInstance()
                .getIntProperty(
                        "netflix.blitz4j." + originalAppenderName
                                + ".discardMapSize", 10000).get();

    }

    public boolean generateBlitz4jLocationInfo() {
        return DynamicPropertyFactory
                .getInstance()
                .getBooleanProperty(
                        "netflix.blitz4j.generateBlitz4jLocationInfo", true)
                .get();

    }

    public boolean generateLog4jLocationInfo() {
        return DynamicPropertyFactory
                .getInstance()
                .getBooleanProperty(
                        "netflix.blitz4j.generateLog4jLocationInfo", false)
                .get();

    }

    public boolean getSummarizeOverflow(String originalAppenderName) {
        return DynamicPropertyFactory
                .getInstance()
                .getBooleanProperty(
                        "netflix.blitz4j" + originalAppenderName
                                + ".summarizeOverflow", true).get();
    }

    public String[] getAsyncAppenderImplementationNames() {
        return DynamicPropertyFactory
                .getInstance()
                .getStringProperty("blitz4j.asyncAppenders",
                        "com.netflix.blitz4j.AsyncAppender").get().split(",");
    }

    public int getBatcherQueueMaxMessages(String batcherName) {

        return DynamicPropertyFactory.getInstance()
                .getIntProperty(batcherName + "." + "queue.maxMessages", 10000)
                .get();
    }

    public int getBatchSize(String batcherName) {
        return DynamicPropertyFactory.getInstance()
                .getIntProperty(batcherName + "." + "batch.maxMessages", 30)
                .get();
    }

    public int getBatcherWaitTimeBeforeShutdown(String batcherName) {
        return DynamicPropertyFactory.getInstance()
                .getIntProperty(batcherName + ".waitTimeinMillis", 10000).get();
    }

    public double getBatcherMaxDelay(String batcherName) {
        return DynamicPropertyFactory.getInstance()
                .getDoubleProperty(batcherName + "." + "batch.maxDelay", 0.5)
                .get();
    }

    public boolean shouldWaitWhenBatcherQueueNotEmpty(String batcherName) {

        return DynamicPropertyFactory.getInstance()
                .getBooleanProperty(batcherName + ".blocking", false).get();

    }

    public int getBatcherMinThreads(String batcherName) {

        return DynamicPropertyFactory.getInstance()
                .getIntProperty(batcherName + ".minThreads", 1).get();

    }

    public int getBatcherMaxThreads(String batcherName) {

        return DynamicPropertyFactory.getInstance()
                .getIntProperty(batcherName + ".maxThreads", 3).get();

    }

    public int getBatcherThreadKeepAliveTime(String batcherName) {

        return DynamicPropertyFactory.getInstance()
                .getIntProperty(batcherName + ".keepAliveTime", 900).get();

    }

    public boolean shouldRejectWhenAllBatcherThreadsUsed(String batcherName) {
        return DynamicPropertyFactory.getInstance()
                .getBooleanProperty(batcherName + ".rejectWhenFull", false)
                .get();
    }
}
