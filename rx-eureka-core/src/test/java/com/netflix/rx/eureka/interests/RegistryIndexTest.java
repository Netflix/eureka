package com.netflix.rx.eureka.interests;

import java.util.ArrayList;
import java.util.List;

import com.netflix.rx.eureka.client.metric.EurekaClientMetricFactory;
import com.netflix.rx.eureka.registry.EurekaRegistry;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistry;
import com.netflix.rx.eureka.server.registry.EurekaServerRegistryImpl;
import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.SampleInstanceInfo;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.functions.Action1;

import static com.netflix.rx.eureka.interests.Interests.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * TODO: Since now we have separate client/server registry implementation we need to move this code to their corresponding registries
 *
 * @author Nitesh Kant
 */
public class RegistryIndexTest {

    private static final InstanceInfo discoveryServer = SampleInstanceInfo.DiscoveryServer.build();
    private static final InstanceInfo zuulServer = SampleInstanceInfo.ZuulServer.build();

    private EurekaRegistry<InstanceInfo, ?> registry;

//    @Rule
//    public final ExternalResource registryResource = new ExternalResource() {
//
//        @Override
//        protected void before() throws Throwable {
//            registry = new EurekaServerRegistryImpl(EurekaClientMetricFactory.clientMetrics().getRegistryMetrics());
//        }
//
//        @Override
//        protected void after() {
//            registry.shutdown();
//        }
//    };

    @Test
    @Ignore
    public void testBasicIndex() throws Exception {
        doTestWithIndex(forFullRegistry());
    }

    @Test
    @Ignore
    public void testCompositeIndex() throws Exception {
        doTestWithIndex(forSome(forInstances(discoveryServer.getId()), forInstances(zuulServer.getId())));
    }

    private void doTestWithIndex(Interest<InstanceInfo> interest) {
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();

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
