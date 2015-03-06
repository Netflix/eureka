package com.netflix.eureka2.simulator;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;

/**
 * @author Tomasz Bak
 */
public class Eureka1ServerTest {

    @Rule
    public final Eureka1ServerResource serverResource = new Eureka1ServerResource();

    @Test(timeout = 60000)
    public void testDiscoveryClient() throws Exception {
        serverResource.createDiscoveryClient("testService");
        serverResource.getServer().assertContainsInstance("testService", 60, TimeUnit.SECONDS);
    }
}