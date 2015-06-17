package com.netflix.eureka2.integration;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import rx.functions.Action1;

import static com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo.collectionOf;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * This is client side counterpart of {@link EurekaDeployment} class,
 * which provides easy means for injecting/verifying large amount of data into/out of the Eureka cluster.
 * This is handy for more complex integration tests.
 *
 * @author Tomasz Bak
 */
public class EurekaDeploymentClients {

    private static final int TIMEOUT_SEC = 30;

    private final EurekaDeployment eurekaDeployment;

    public EurekaDeploymentClients(EurekaDeployment eurekaDeployment) {
        this.eurekaDeployment = eurekaDeployment;
    }

    public void fillUpRegistryOfServer(int serverIdx, int count, InstanceInfo instanceTemplate) throws Exception {
        Iterator<InstanceInfo> instanceIt = collectionOf(instanceTemplate.getApp(), instanceTemplate);
        Source source = new Source(Origin.LOCAL, "write" + serverIdx);
        SourcedEurekaRegistry<InstanceInfo> eurekaServerRegistry = eurekaDeployment.getWriteCluster().getServer(serverIdx).getEurekaServerRegistry();

        final Set<String> expectedInstances = new HashSet<>();
        for (int i = 0; i < count; i++) {
            InstanceInfo next = instanceIt.next();
            eurekaServerRegistry.register(next, source).subscribe(new NoOpSubscriber<Boolean>());
            expectedInstances.add(next.getId());
        }

        final CountDownLatch latch = new CountDownLatch(expectedInstances.size());

        eurekaServerRegistry.forInterest(Interests.forApplications(instanceTemplate.getApp()))
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.getKind() == Kind.Add) {
                            String id = notification.getData().getId();
                            if (expectedInstances.remove(id)) {
                                latch.countDown();
                            }
                        }
                    }
                });

        assertTrue("Registry not ready in time", latch.await(30, TimeUnit.SECONDS));
    }

    public void fillUpRegistry(int count, InstanceInfo instanceTemplate) throws Exception {
        fillUpRegistryOfServer(0, count, instanceTemplate);
    }

    public void verifyWriteServerRegistryContent(int writeServerIdx, String appName, int count) throws InterruptedException {
        SourcedEurekaRegistry<InstanceInfo> registry = registryOf(writeServerIdx);

        final CountDownLatch latch = new CountDownLatch(count);

        registry.forInterest(Interests.forApplications(appName))
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.getKind() == Kind.Add) {
                            latch.countDown();
                        }
                    }
                });

        assertTrue("Registry not ready in time", latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
    }

    public void verifyWriteServerHasNoInstance(int writeServerIdx, String appName) throws InterruptedException {
        SourcedEurekaRegistry<InstanceInfo> registry = registryOf(writeServerIdx);

        final CountDownLatch latch = new CountDownLatch(1);

        /**
         * TODO We cannot trust buffer markers yet, so we subscribe and wait for some time to be sure there is no data for the given app
         */
        registry.forInterest(Interests.forApplications(appName))
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.getKind() == Kind.Add) {
                            latch.countDown();
                        }
                    }
                });
        assertFalse("Unexpected items in the registry found", latch.await(1, TimeUnit.SECONDS));
    }

    private SourcedEurekaRegistry<InstanceInfo> registryOf(int writeServerIdx) {
        EmbeddedWriteServer server = eurekaDeployment.getWriteCluster().getServer(writeServerIdx);
        return server.getEurekaServerRegistry();
    }
}
