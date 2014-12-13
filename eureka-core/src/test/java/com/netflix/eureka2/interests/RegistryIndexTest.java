package com.netflix.eureka2.interests;

import com.netflix.eureka2.client.metric.EurekaClientMetricFactory;
import com.netflix.eureka2.client.registry.EurekaClientRegistryImpl;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.functions.Action1;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.ArrayList;
import java.util.List;

import static com.netflix.eureka2.interests.Interests.forFullRegistry;
import static com.netflix.eureka2.interests.Interests.forInstances;
import static com.netflix.eureka2.interests.Interests.forSome;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

/**
 * @author Nitesh Kant
 */
public class RegistryIndexTest {

    private InstanceInfo.Builder discoveryServerBuilder;
    private InstanceInfo.Builder zuulServerBuilder;
    private InstanceInfo.Builder cliServerBuilder;

    private InstanceInfo discoveryServer;
    private InstanceInfo zuulServer;
    private InstanceInfo cliServer;

    private TestScheduler testScheduler;
    private EurekaRegistry<InstanceInfo, ?> registry;

    @Rule
    public final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            discoveryServerBuilder = SampleInstanceInfo.DiscoveryServer.builder();
            zuulServerBuilder = SampleInstanceInfo.ZuulServer.builder();
            cliServerBuilder = SampleInstanceInfo.CliServer.builder();

            discoveryServer = discoveryServerBuilder.build();
            zuulServer = zuulServerBuilder.build();
            cliServer = cliServerBuilder.build();

            testScheduler = Schedulers.test();
            registry = new EurekaClientRegistryImpl(EurekaClientMetricFactory.clientMetrics().getRegistryMetrics());
        }

        @Override
        protected void after() {
            registry.shutdown();
        }
    };

    @Test
    public void testBasicIndex() throws Exception {
        List<ChangeNotification<InstanceInfo>> notifications = doTestWithIndex(forFullRegistry());

        assertThat(notifications, hasSize(5));

        InstanceInfo newCliServer = cliServerBuilder.withStatus(InstanceInfo.Status.DOWN).build();

        assertThat(notifications,  // Checks the order of notifications.
                contains(new ChangeNotification<>(Kind.Add, discoveryServer),
                        new ChangeNotification<>(Kind.Add, zuulServer),
                        new ChangeNotification<>(Kind.Delete, discoveryServer),
                        new ChangeNotification<>(Kind.Add, cliServer),
                        new ModifyNotification<>(newCliServer, newCliServer.diffOlder(cliServer))));
    }

    @Test
    public void testCompositeIndex() throws Exception {
        List<ChangeNotification<InstanceInfo>> notifications =
                doTestWithIndex(forSome(forInstances(discoveryServer.getId()), forInstances(zuulServer.getId())));

        assertThat(notifications, hasSize(3));
        assertThat(notifications,  // Checks the order of notifications.
                contains(new ChangeNotification<>(Kind.Add, discoveryServer),
                        new ChangeNotification<>(Kind.Add, zuulServer),
                        new ChangeNotification<>(Kind.Delete, discoveryServer)));
    }

    private List<ChangeNotification<InstanceInfo>> doTestWithIndex(Interest<InstanceInfo> interest) {
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();

        registry.register(discoveryServer).subscribeOn(testScheduler).subscribe();
        registry.forInterest(interest)
                .subscribeOn(testScheduler)
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        notifications.add(notification);
                    }
                });
        testScheduler.triggerActions();

        registry.register(zuulServer).subscribeOn(testScheduler).subscribe();
        registry.unregister(discoveryServer).subscribeOn(testScheduler).subscribe();
        registry.register(cliServer).subscribeOn(testScheduler).subscribe();
        InstanceInfo newCliServer = cliServerBuilder.withStatus(InstanceInfo.Status.DOWN).build();
        registry.update(newCliServer, newCliServer.diffOlder(cliServer)).subscribeOn(testScheduler).subscribe();;

        testScheduler.triggerActions();

        return notifications;
    }
}
