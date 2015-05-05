package com.netflix.eureka2.shaded;

import com.netflix.eureka2.client.transport.Eureka2ClientShadingTestClass;
import com.netflix.eureka2.transport.Eureka2CoreShadingTestClass;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author David Liu
 */
public class ShadedClientTest {

    @Test
    public void testRelocatedRxNetty() {
        String packageNameInCore = Eureka2CoreShadingTestClass.TEST_CLIENT_BUILDER.getClass().getPackage().getName();
        Assert.assertTrue("package in core started with: " + packageNameInCore, packageNameInCore.startsWith("com.eureka2.shading.reactivex.netty"));

        String packageNameInClient = Eureka2ClientShadingTestClass.TEST_CLIENT_BUILDER.getClass().getPackage().getName();
        Assert.assertTrue("package in client started with: " + packageNameInClient, packageNameInClient.startsWith("com.eureka2.shading.reactivex.netty"));
    }
}
