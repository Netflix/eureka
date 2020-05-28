package com.netflix.discovery;

import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import org.junit.Assert;
import org.junit.Test;

public class DiscoveryClientInstanceStatusChangeGateTest extends AbstractDiscoveryClientTester {

    @Override
    protected void setupProperties() {
        super.setupProperties();
        ConfigurationManager.getConfigInstance().setProperty("eureka.registration.enabled", "true");
        // as the tests in this class triggers the instanceInfoReplicator explicitly, set the below config
        // so that it does not run as a background task
        ConfigurationManager.getConfigInstance().setProperty("eureka.appinfo.initial.replicate.time", Integer.MAX_VALUE);
        ConfigurationManager.getConfigInstance().setProperty("eureka.appinfo.replicate.interval", Integer.MAX_VALUE);
    }

    @Override
    protected InstanceInfo.Builder newInstanceInfoBuilder(int renewalIntervalInSecs) {
        InstanceInfo.Builder builder = super.newInstanceInfoBuilder(renewalIntervalInSecs);
        builder.setStatus(InstanceInfo.InstanceStatus.STARTING);
        return builder;
    }

    @Test
    public void testAddGateSuccess() throws Exception {
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        Assert.assertTrue(clientImpl.addGate(InstanceInfo.InstanceStatus.UP, newStatus -> true));
    }

    @Test
    public void testAddGateFailure() throws Exception {
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        EurekaInstanceStatusChangeGate gate = newStatus -> true;
        Assert.assertTrue(clientImpl.addGate(InstanceInfo.InstanceStatus.UP, gate));
        Assert.assertFalse(clientImpl.addGate(InstanceInfo.InstanceStatus.UP, gate));
    }

    @Test
    public void testIsNotAllowedSetsStatusToDown() throws Exception {
        ExampleHealthCheckHandler healthCheckHandler = new ExampleHealthCheckHandler(InstanceInfo.InstanceStatus.UP);
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        clientImpl.addGate(InstanceInfo.InstanceStatus.UP, newStatus -> false);

        InstanceInfoReplicator replicator = clientImpl.getInstanceInfoReplicator();
        Assert.assertEquals("InstanceInfo status not expected", InstanceInfo.InstanceStatus.STARTING, clientImpl.getInstanceInfo().getStatus());
        client.registerHealthCheck(healthCheckHandler);
        replicator.run();
        Assert.assertTrue("HealthCheck callback not invoked", healthCheckHandler.isInvoked());
        Assert.assertEquals("InstanceInfo status not expected", InstanceInfo.InstanceStatus.DOWN, clientImpl.getInstanceInfo().getStatus());
    }

    @Test
    public void testIsAllowedSetsStatusGiven() throws Exception {
        ExampleHealthCheckHandler healthCheckHandler = new ExampleHealthCheckHandler(InstanceInfo.InstanceStatus.UP);
        Assert.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;
        clientImpl.addGate(InstanceInfo.InstanceStatus.UP, newStatus -> true);

        InstanceInfoReplicator replicator = clientImpl.getInstanceInfoReplicator();
        Assert.assertEquals("InstanceInfo status not expected", InstanceInfo.InstanceStatus.STARTING, clientImpl.getInstanceInfo().getStatus());
        client.registerHealthCheck(healthCheckHandler);
        replicator.run();
        Assert.assertTrue("HealthCheck callback not invoked", healthCheckHandler.isInvoked());
        Assert.assertEquals("InstanceInfo status not expected", InstanceInfo.InstanceStatus.UP, clientImpl.getInstanceInfo().getStatus());
    }

    private static class ExampleHealthCheckHandler implements HealthCheckHandler {

        private final InstanceInfo.InstanceStatus health;
        private volatile boolean invoked;
        volatile boolean shouldException;

        private ExampleHealthCheckHandler(InstanceInfo.InstanceStatus health) {
            this.health = health;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public void reset() {
            shouldException = false;
            invoked = false;
        }

        @Override
        public InstanceInfo.InstanceStatus getStatus(InstanceInfo.InstanceStatus currentStatus) {
            invoked = true;
            if (shouldException) {
                throw new RuntimeException("test induced exception");
            }
            return health;
        }
    }

}
