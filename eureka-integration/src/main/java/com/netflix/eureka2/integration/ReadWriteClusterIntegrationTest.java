package com.netflix.eureka2.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.WriteServerResolverSet;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.BasicDataCenterInfo;
import com.netflix.eureka2.testkit.embedded.EmbeddedEurekaCluster;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Subscriber;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * TODO: tweak embedded cluster eviction queue settings to evict faster
 *
 * @author David Liu
 */
public class ReadWriteClusterIntegrationTest {

    private static EmbeddedEurekaCluster eurekaCluster;

    private EurekaClient eurekaClient;
    private InstanceInfo registeringInstanceInfo;

    @ClassRule
    public static final ExternalResource embeddedCluster = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            eurekaCluster = new EmbeddedEurekaCluster(3, 6, false);  // 3 write, 6 read, no bridge
            Thread.sleep(1000);  // give the cluster some init time
        }

        @Override
        protected void after() {
//            eurekaCluster.shutdown();  // FIXME: provide dummy metrics first before enable shutdown
        }
    };

    @Rule
    public final ExternalResource testClient = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            eurekaClient = Eureka.newClientBuilder(WriteServerResolverSet.just("localhost", 13100, 13101), "ReadServer").build();

            registeringInstanceInfo = new InstanceInfo.Builder()
                    .withId("id#testClient")
                    .withApp("app#testClient")
                    .withAppGroup("appGroup#testClient")
                    .withVipAddress("vip#testClient")
                    .withStatus(InstanceInfo.Status.UP)
                    .withDataCenterInfo(BasicDataCenterInfo.fromSystemData())
                    .build();
        }

        @Override
        protected void after() {
            eurekaClient.close();
        }
    };



    @Test
    public void ReadWriteClusterRegistrationTest() throws Exception {
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();

        final CountDownLatch completionLatch = new CountDownLatch(1);
        eurekaClient.forInterest(Interests.forApplications("app#testClient"))
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        Assert.fail("Should not onComplete");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Assert.fail("Should not onError");
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        System.out.println(notification);
                        completionLatch.countDown();
                        notifications.add(notification);
                    }
                });

        eurekaClient.register(registeringInstanceInfo).subscribe();
//        eurekaClient.unregister(registeringInstanceInfo).subscribe();

        completionLatch.await(5, TimeUnit.SECONDS);

        assertThat(notifications.size(), equalTo(1));

        assertThat(notifications.get(0).getData().getApp(), equalTo(registeringInstanceInfo.getApp()));
        assertThat(notifications.get(0).getKind(), equalTo(ChangeNotification.Kind.Add));

//        assertThat(notifications.get(1).getData().getApp(), equalTo(registeringInstanceInfo.getApp()));
//        assertThat(notifications.get(1).getKind(), equalTo(ChangeNotification.Kind.Delete));
    }


}
