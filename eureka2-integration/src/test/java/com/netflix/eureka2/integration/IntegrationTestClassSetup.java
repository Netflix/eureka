package com.netflix.eureka2.integration;

import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * @author David Liu
 */
public abstract class IntegrationTestClassSetup {

    @BeforeClass
    public static void setUpClass() {
        System.setProperty("eureka.hacks.interestChannel.bufferHintDelayMs", "10");
        System.setProperty("eureka.hacks.interestChannel.maxBufferHintDelayMs", "100");
        System.setProperty("eureka.hacks.receiverReplicationChannel.bufferHintDelayMs", "10");
        System.setProperty("eureka.hacks.receiverReplicationChannel.maxBufferHintDelayMs", "100");
    }

    @AfterClass
    public static void tearDownClass() {
        System.clearProperty("eureka.hacks.interestChannel.bufferHintDelayMs");
        System.clearProperty("eureka.hacks.interestChannel.maxBufferHintDelayMs");
        System.clearProperty("eureka.hacks.receiverReplicationChannel.bufferHintDelayMs");
        System.clearProperty("eureka.hacks.receiverReplicationChannel.maxBufferHintDelayMs");
    }
}
