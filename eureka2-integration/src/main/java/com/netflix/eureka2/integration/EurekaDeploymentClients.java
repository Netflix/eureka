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
import com.netflix.eureka2.utils.rx.NoOpSubscriber;
import rx.functions.Action1;

import static com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo.collectionOf;
import static org.junit.Assert.assertTrue;

/**
 * This is client side counterpart of {@link EurekaDeployment} class,
 * which provides easy means for injecting/verifying large amount of data into/out of the Eureka cluster.
 * This is handy for more complex integration tests.
 *
 * @author Tomasz Bak
 */
public class EurekaDeploymentClients {

    private final EurekaDeployment eurekaDeployment;

    public EurekaDeploymentClients(EurekaDeployment eurekaDeployment) {
        this.eurekaDeployment = eurekaDeployment;
    }

    public void fillUpRegistry(int count, InstanceInfo instanceTemplate) throws Exception {
        Iterator<InstanceInfo> instanceIt = collectionOf(instanceTemplate.getApp(), instanceTemplate);
        Source source = new Source(Origin.LOCAL, "write0");
        SourcedEurekaRegistry<InstanceInfo> eurekaServerRegistry = eurekaDeployment.getWriteCluster().getServer(0).getEurekaServerRegistry();

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
}
