package com.netflix.eureka2.interests;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.metric.EurekaRegistryMetricFactory;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.SourcedEurekaRegistryImpl;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.functions.Action1;
import rx.functions.Func1;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.interests.Interests.forFullRegistry;
import static com.netflix.eureka2.interests.Interests.forInstances;
import static com.netflix.eureka2.interests.Interests.forSome;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
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

    private SourcedEurekaRegistry<InstanceInfo> registry;
    private Source localSource;

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

            registry = new SourcedEurekaRegistryImpl(EurekaRegistryMetricFactory.registryMetrics());
            localSource = Source.localSource(UUID.randomUUID().toString());
        }

        @Override
        protected void after() {
            registry.shutdown();
        }
    };


    @Test(timeout = 10000)
    public void testBasicIndex() throws Exception {
        List<ChangeNotification<InstanceInfo>> notifications = doTestWithIndex(forFullRegistry(), 5);

        assertThat(notifications, hasSize(5));
        InstanceInfo newCliServer = cliServerBuilder.withStatus(InstanceInfo.Status.DOWN).build();

        assertThat(notifications,  // Checks the order of notifications.
                contains(new ChangeNotification<>(Kind.Add, discoveryServer),
                        new ChangeNotification<>(Kind.Add, zuulServer),
                        new ChangeNotification<>(Kind.Delete, discoveryServer),
                        new ChangeNotification<>(Kind.Add, cliServer),
                        new ModifyNotification<>(newCliServer, newCliServer.diffOlder(cliServer))));
    }

    @Test(timeout = 10000)
    public void testCompositeIndex() throws Exception {
        List<ChangeNotification<InstanceInfo>> notifications =
                doTestWithIndex(forSome(forInstances(discoveryServer.getId()), forInstances(zuulServer.getId())), 3);

        assertThat(notifications, hasSize(3));
        assertThat(notifications,  // Checks the order of notifications.
                contains(new ChangeNotification<>(Kind.Add, discoveryServer),
                        new ChangeNotification<>(Kind.Add, zuulServer),
                        new ChangeNotification<>(Kind.Delete, discoveryServer)));
    }

    private List<ChangeNotification<InstanceInfo>> doTestWithIndex(Interest<InstanceInfo> interest, final int expectedCount) throws Exception {
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();

        final CountDownLatch expectedLatch = new CountDownLatch(expectedCount);
        registry.register(discoveryServer, localSource).toBlocking().firstOrDefault(null);
        registry.forInterest(interest)
                .map(new Func1<ChangeNotification<InstanceInfo>, ChangeNotification<InstanceInfo>>() {  // transform from source version to base version for testing equals
                    @Override
                    public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification instanceof SourcedChangeNotification) {
                            return ((SourcedChangeNotification<InstanceInfo>) notification).toBaseNotification();
                        } else if (notification instanceof SourcedModifyNotification) {
                            return ((SourcedModifyNotification<InstanceInfo>) notification).toBaseNotification();
                        }
                        return notification;
                    }
                })
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        notifications.add(notification);
                        expectedLatch.countDown();
                    }
                });

        registry.register(zuulServer, localSource).toBlocking().firstOrDefault(null);
        registry.unregister(discoveryServer, localSource).toBlocking().firstOrDefault(null);
        registry.register(cliServer, localSource).toBlocking().firstOrDefault(null);
        InstanceInfo newCliServer = cliServerBuilder.withStatus(InstanceInfo.Status.DOWN).build();
        registry.register(newCliServer, localSource).toBlocking().firstOrDefault(null);

        assertThat(expectedLatch.await(1, TimeUnit.MINUTES), equalTo(true));

        return notifications;
    }
}
