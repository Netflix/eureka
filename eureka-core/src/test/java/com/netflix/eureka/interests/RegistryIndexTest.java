package com.netflix.eureka.interests;

import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.LeasedInstanceRegistry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.functions.Action1;

import java.util.ArrayList;
import java.util.List;

import static com.netflix.eureka.interests.Interests.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Nitesh Kant
 */
public class RegistryIndexTest {

    private static final InstanceInfo discoveryServer = SampleInstanceInfo.DiscoveryServer.build();
    private static final InstanceInfo zuulServer = SampleInstanceInfo.ZuulServer.build();

    private EurekaRegistry<InstanceInfo> registry;

    @Rule
    public final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            registry = new LeasedInstanceRegistry(SampleInstanceInfo.DiscoveryServer.build());
        }

        @Override
        protected void after() {
            registry.shutdown();
        }
    };

    @Test
    public void testBasicIndex() throws Exception {
        doTestWithIndex(forFullRegistry());
    }

    @Test
    public void testCompositeIndex() throws Exception {
        doTestWithIndex(forSome(forInstance(discoveryServer.getId()), forInstance(zuulServer.getId())));
    }

    private void doTestWithIndex(Interest<InstanceInfo> interest) {
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<ChangeNotification<InstanceInfo>>();

        registry.register(discoveryServer).toBlocking().lastOrDefault(null);
        registry.forInterest(interest)
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        System.out.println("Notification: " + notification);
                        notifications.add(notification);
                    }
                });
        registry.register(zuulServer).toBlocking().lastOrDefault(null);

        assertThat(notifications, hasSize(2));
        assertThat(notifications,
                contains(SampleChangeNotification.DiscoveryAdd.newNotification(discoveryServer),
                        SampleChangeNotification.ZuulAdd.newNotification(zuulServer))); // Checks the order of notifications.
    }
}
