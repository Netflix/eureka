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

import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.logging.messaging.BatcherFactory;
import com.netflix.logging.messaging.MessageBatcher;
import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.CompositeMonitor;
import com.netflix.servo.monitor.Monitor;

public class TestBlitz4j {
    protected Properties props = new Properties();
    private static final String consoleSummarizeEvent = "stdout_summarizeEvent";
    private static final String consoleEventsProcessed = "stdout_putInBuffer";

    @After
    public void tearDown() throws Exception {
        props.clear();
        MessageBatcher asyncBatcherConsole = BatcherFactory
                .getBatcher(AsyncAppender.class.getName() + "." + "stdout");
        Assert.assertNull(asyncBatcherConsole);
    }

  
    @Test
    public void testAsyncAppenders() throws Exception {
        props.setProperty("log4j.rootCategory", "OFF");
        props.setProperty("log4j.logger.com.netflix.blitz4j.TestBlitz4j",
                "INFO,stdout");
        props.setProperty("log4j.logger.com.netflix.blitz4j.TestBlitz4j$1",
                "INFO,stdout");
        props.setProperty("log4j.appender.stdout",
                "org.apache.log4j.ConsoleAppender");
        props.setProperty("log4j.appender.stdout.layout",
                "com.netflix.logging.log4jAdapter.NFPatternLayout");
        props.setProperty("log4j.appender.stdout.layout.ConversionPattern",
                "%d %-5p %C:%L [%t] [%M] %m%n");

        props.setProperty("log4j.logger.asyncAppenders", "INFO,stdout");
        props.setProperty(
                "batcher.com.netflix.logging.AsyncAppender.stdout.waitTimeinMillis",
                "120000");
        LoggingConfiguration.getInstance().configure(props);
        int noOfThreads = 100;
        Thread[] tArray = new Thread[noOfThreads];
        for (int i = 0; i < noOfThreads; i++) {
            Thread t1 = new Thread(new Runnable() {

                public void run() {
                    int i = 0;

                    while (i < 1000) {
                        i++;
                        Logger slflogger = LoggerFactory
                                .getLogger(TestBlitz4j.class);
                        slflogger.info("Testing named log with this string {}",
                                "Test String");
                        Thread.yield();
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }

            });
            tArray[i] = t1;
        }

        Thread t2 = new Thread(new Runnable() {

            public void run() {
                int i = 0;

                while (i <= 1000) {
                    try {
                        ConfigurationManager.getConfigInstance().setProperty(
                                "log4j.junk", (i++) + "");
                        Thread.sleep(10);
                        // NetflixConfiguration.getInstance().setProperty("log4j.junk1",
                        // (i++) + "");
                        // Thread.sleep(10);
                        Thread.yield();

                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

        });
        for (int i = 0; i < noOfThreads; i++) {
            tArray[i].start();
        }
        t2.start();
        t2.join();
        for (int i = 0; i < noOfThreads; i++) {
            tArray[i].join();
        }

        int numSummarizedConsole = 0;
        try {
            numSummarizedConsole = Integer
                    .valueOf(getMonitoringData(consoleSummarizeEvent));
        } catch (Throwable e) {

        }
        int numAddedConsole = Integer
                .valueOf(getMonitoringData(consoleEventsProcessed));
        System.out
                .println("The number of messages added to async batcher console: "
                        + numAddedConsole);
        System.out
                .println("The number of messages summarized from async batcher console : "
                        + numSummarizedConsole);
        System.out.println("Total number of messages to asyncBatcher console: "
                + (numAddedConsole + numSummarizedConsole));
        Assert.assertTrue(((numAddedConsole + numSummarizedConsole) >= 100000));
        LoggingConfiguration.getInstance().stop();

    }

    @Test
    public void testReconfiguration() throws Exception {
        Properties props = new Properties();
        props.setProperty("log4j.rootCategory", "INFO,stdout");
        props.setProperty("log4j.appender.stdout",
                "org.apache.log4j.ConsoleAppender");
        props.setProperty("log4j.appender.stdout.layout",
                "com.netflix.logging.log4jAdapter.NFPatternLayout");
        props.setProperty("log4j.appender.stdout.layout.ConversionPattern",
                "%d %-5p %C:%L [%t] [%M] %m%n");
        props.setProperty("log4j.logger.asyncAppenders", "INFO,stdout");
        LoggingConfiguration.getInstance().configure(props);
        org.slf4j.Logger slfLogger = LoggerFactory.getLogger(this.getClass());
        ConfigurationManager.getConfigInstance().setProperty(
                "log4j.logger.com.netflix.blitz4j.TestBlitz4j", "DEBUG");

        Thread.sleep(5000);
        Assert.assertTrue(slfLogger.isDebugEnabled());
        slfLogger.debug("You should see this");
        ConfigurationManager.getConfigInstance().setProperty(
                "log4j.logger.com.netflix.blitz4j.TestBlitz4j", "INFO");

        Thread.sleep(4000);
        Assert.assertFalse(slfLogger.isDebugEnabled());
        LoggingConfiguration.getInstance().stop();
    }

    private String getMonitoringData(String metricName) {
        Collection monitors = DefaultMonitorRegistry.getInstance()
                .getRegisteredMonitors();
        for (Object m : monitors) {
            if (CompositeMonitor.class.isInstance(m)) {
                CompositeMonitor monitor = (CompositeMonitor) m;
                List<Monitor> monitorsList = monitor.getMonitors();
                for (Monitor m1 : monitorsList) {
                    if (metricName.equalsIgnoreCase(m1.getConfig().getName())) {

                        return m1.getValue() + "";
                    }

                }
            }

        }
        return null;
    }

}
