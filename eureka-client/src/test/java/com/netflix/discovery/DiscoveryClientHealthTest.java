package com.netflix.discovery;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Nitesh Kant
 */
public class DiscoveryClientHealthTest extends AbstractDiscoveryClientTester {

    @Override
    protected void setupProperties() {
        super.setupProperties();
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "true");
    }

    @Override
    protected InstanceInfo.Builder newInstanceInfoBuilder(int renewalIntervalInSecs) {
        InstanceInfo.Builder builder = super.newInstanceInfoBuilder(renewalIntervalInSecs);
        builder.setStatus(InstanceInfo.InstanceStatus.STARTING);
        return builder;
    }

    @Test
    public void testCallback() throws Exception {
        MyHealthCheckCallback myCallback = new MyHealthCheckCallback(true);
        client.registerHealthCheckCallback(myCallback);
        DiscoveryClient.InstanceInfoReplicator instanceInfoReplicator = client.getInstanceInfoReplicator();
        
        // DEFAULT: STARTING 
        myCallback.reset();
        instanceInfoReplicator.run();

        Assert.assertEquals(InstanceInfo.InstanceStatus.STARTING, client.getInstanceInfo().getStatus());
        Assert.assertFalse(myCallback.isInvoked());

        // OUT_OF_SERVICE
        myCallback.reset();
        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);
        instanceInfoReplicator.run();
        
        Assert.assertEquals(InstanceInfo.InstanceStatus.OUT_OF_SERVICE, client.getInstanceInfo().getStatus());
        Assert.assertFalse(myCallback.isInvoked());

        // DOWN
        myCallback.reset();
        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
        instanceInfoReplicator.run();
        
        Assert.assertEquals(InstanceInfo.InstanceStatus.DOWN, client.getInstanceInfo().getStatus());
        Assert.assertFalse(myCallback.isInvoked());

        // UP
        myCallback.reset();
        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        instanceInfoReplicator.run();

        Assert.assertTrue(myCallback.isInvoked());
        Assert.assertEquals(InstanceInfo.InstanceStatus.UP, client.getInstanceInfo().getStatus());
    }

    @Test
    public void testHandler() throws Exception {
        MyHealthCheckHandler myHealthCheckHandler = new MyHealthCheckHandler(InstanceInfo.InstanceStatus.UP);
        client.registerHealthCheck(myHealthCheckHandler);
        DiscoveryClient.InstanceInfoReplicator instanceInfoReplicator = client.getInstanceInfoReplicator();

        Assert.assertEquals(InstanceInfo.InstanceStatus.STARTING,client.getInstanceInfo().getStatus());
        
        // DEFAULT: Starting
        myHealthCheckHandler.reset();
        instanceInfoReplicator.run();

        Assert.assertFalse(myHealthCheckHandler.isInvoked());
        Assert.assertEquals(InstanceInfo.InstanceStatus.STARTING, client.getInstanceInfo().getStatus());

        // OOS
        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);
        myHealthCheckHandler.reset();
        instanceInfoReplicator.run();

        Assert.assertFalse(myHealthCheckHandler.isInvoked());
        Assert.assertEquals(InstanceInfo.InstanceStatus.OUT_OF_SERVICE,   client.getInstanceInfo().getStatus());

        // DOWN
        myHealthCheckHandler.reset();
        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.DOWN);
        instanceInfoReplicator.run();
        
        Assert.assertFalse(myHealthCheckHandler.isInvoked());
        Assert.assertEquals(InstanceInfo.InstanceStatus.DOWN, client.getInstanceInfo().getStatus());

        // UP
        ApplicationInfoManager.getInstance().setInstanceStatus(InstanceInfo.InstanceStatus.UP);
        instanceInfoReplicator.run();
        
        Assert.assertTrue(myHealthCheckHandler.isInvoked());
        Assert.assertEquals(InstanceInfo.InstanceStatus.UP, client.getInstanceInfo().getStatus());
    }

    private static class MyHealthCheckCallback implements HealthCheckCallback {

        private final boolean health;
        private volatile boolean invoked;

        private MyHealthCheckCallback(boolean health) {
            this.health = health;
        }

        @Override
        public boolean isHealthy() {
            invoked = true;
            return health;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public void reset() {
            invoked = false;
        }
    }

    private static class MyHealthCheckHandler implements HealthCheckHandler {

        private final InstanceInfo.InstanceStatus health;
        private volatile boolean invoked;

        private MyHealthCheckHandler(InstanceInfo.InstanceStatus health) {
            this.health = health;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public void reset() {
            invoked = false;
        }

        @Override
        public InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus currentStatus) {
            invoked = true;
            return health;
        }
    }
}
