package com.netflix.discovery;

import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.config.ConfigurationManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;

/**
 * @author Nitesh Kant
 */
public class DiscoveryClientHealthTest extends AbstractDiscoveryClientTester {

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
    public void testCallback() throws Exception {
        MyHealthCheckCallback myCallback = new MyHealthCheckCallback(true);

        Assertions.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;

        InstanceInfoReplicator instanceInfoReplicator = clientImpl.getInstanceInfoReplicator();
        instanceInfoReplicator.run();

        Assertions.assertEquals(InstanceInfo.InstanceStatus.STARTING,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected.");
        Assertions.assertFalse(myCallback.isInvoked(), "Healthcheck callback invoked when status is STARTING.");

        client.registerHealthCheckCallback(myCallback);

        clientImpl.getInstanceInfo().setStatus(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);
        Assertions.assertEquals(InstanceInfo.InstanceStatus.OUT_OF_SERVICE,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected.");

        myCallback.reset();
        instanceInfoReplicator.run();
        Assertions.assertFalse(myCallback.isInvoked(), "Healthcheck callback invoked when status is OOS.");

        clientImpl.getInstanceInfo().setStatus(InstanceInfo.InstanceStatus.DOWN);
        Assertions.assertEquals(InstanceInfo.InstanceStatus.DOWN,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected.");
        myCallback.reset();
        instanceInfoReplicator.run();

        Assertions.assertTrue(myCallback.isInvoked(), "Healthcheck callback not invoked.");
        Assertions.assertEquals(InstanceInfo.InstanceStatus.UP,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected.");
    }

    @Test
    public void testHandler() throws Exception {
        MyHealthCheckHandler myHealthCheckHandler = new MyHealthCheckHandler(InstanceInfo.InstanceStatus.UP);

        Assertions.assertTrue(client instanceof DiscoveryClient);
        DiscoveryClient clientImpl = (DiscoveryClient) client;

        InstanceInfoReplicator instanceInfoReplicator = clientImpl.getInstanceInfoReplicator();

        Assertions.assertEquals(InstanceInfo.InstanceStatus.STARTING,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected.");

        client.registerHealthCheck(myHealthCheckHandler);

        instanceInfoReplicator.run();

        Assertions.assertTrue(myHealthCheckHandler.isInvoked(), "Healthcheck callback not invoked when status is STARTING.");
        Assertions.assertEquals(InstanceInfo.InstanceStatus.UP,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected post healthcheck.");

        clientImpl.getInstanceInfo().setStatus(InstanceInfo.InstanceStatus.OUT_OF_SERVICE);
        Assertions.assertEquals(InstanceInfo.InstanceStatus.OUT_OF_SERVICE,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected.");
        myHealthCheckHandler.reset();
        instanceInfoReplicator.run();

        Assertions.assertTrue(myHealthCheckHandler.isInvoked(), "Healthcheck callback not invoked when status is OUT_OF_SERVICE.");
        Assertions.assertEquals(InstanceInfo.InstanceStatus.UP,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected post healthcheck.");

        clientImpl.getInstanceInfo().setStatus(InstanceInfo.InstanceStatus.DOWN);
        Assertions.assertEquals(InstanceInfo.InstanceStatus.DOWN,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected.");
        myHealthCheckHandler.reset();
        instanceInfoReplicator.run();

        Assertions.assertTrue(myHealthCheckHandler.isInvoked(), "Healthcheck callback not invoked when status is DOWN.");
        Assertions.assertEquals(InstanceInfo.InstanceStatus.UP,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected post healthcheck.");

        clientImpl.getInstanceInfo().setStatus(InstanceInfo.InstanceStatus.UP);
        myHealthCheckHandler.reset();
        myHealthCheckHandler.shouldException = true;
        instanceInfoReplicator.run();

        Assertions.assertTrue(myHealthCheckHandler.isInvoked(), "Healthcheck callback not invoked when status is UP.");
        Assertions.assertEquals(InstanceInfo.InstanceStatus.DOWN,
                clientImpl.getInstanceInfo().getStatus(),
                "Instance info status not as expected post healthcheck.");
    }

    @Test
    public void shouldRegisterHealthCheckHandlerInConcurrentEnvironment() throws Exception {
        HealthCheckHandler myHealthCheckHandler = new MyHealthCheckHandler(InstanceInfo.InstanceStatus.UP);

        int testsCount = 20;
        int threadsCount = testsCount * 2;
        CountDownLatch starterLatch = new CountDownLatch(threadsCount);
        CountDownLatch finishLatch = new CountDownLatch(threadsCount);

        List<DiscoveryClient> discoveryClients = range(0, testsCount)
                .mapToObj(i -> (DiscoveryClient) getSetupDiscoveryClient())
                .collect(toList());

        Stream<Thread> registerCustomHandlerThreads = discoveryClients.stream().map(client ->
                new SimultaneousStarter(starterLatch, finishLatch, () -> client.registerHealthCheck(myHealthCheckHandler)));
        Stream<Thread> lazyInitOfDefaultHandlerThreads = discoveryClients.stream().map(client ->
                new SimultaneousStarter(starterLatch, finishLatch, client::getHealthCheckHandler));
        List<Thread> threads = Stream.concat(registerCustomHandlerThreads, lazyInitOfDefaultHandlerThreads)
                .collect(toList());
        Collections.shuffle(threads);
        threads.forEach(Thread::start);

       try {
            finishLatch.await();
            discoveryClients.forEach(client ->
                    Assertions.assertSame(myHealthCheckHandler, client.getHealthCheckHandler(), "Healthcheck handler should be custom."));
        } finally {
            //cleanup resources
            discoveryClients.forEach(DiscoveryClient::shutdown);
        }
    }

    public static class SimultaneousStarter extends Thread {

        private final CountDownLatch starterLatch;
        private final CountDownLatch finishLatch;
        private final Runnable runnable;

        public SimultaneousStarter(CountDownLatch starterLatch, CountDownLatch finishLatch, Runnable runnable) {
            this.starterLatch = starterLatch;
            this.finishLatch = finishLatch;
            this.runnable = runnable;
        }

        @Override
        public void run() {
            starterLatch.countDown();
            try {
                starterLatch.await();
                runnable.run();
                finishLatch.countDown();
            } catch (InterruptedException e) {
                throw new RuntimeException("Something went wrong...");
            }
        }
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
        volatile boolean shouldException;

        private MyHealthCheckHandler(InstanceInfo.InstanceStatus health) {
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
