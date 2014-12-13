package com.netflix.eureka2.integration;

import java.util.ArrayList;
import java.util.List;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.registry.datacenter.BasicDataCenterInfo;
import com.netflix.eureka2.testkit.embedded.EmbeddedEurekaCluster;
import com.netflix.eureka2.transport.EurekaTransports;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Subscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * TODO: use testSchedulers instead of using Thread.sleep()
 * TODO: tweak embedded cluster eviction queue settings to evict faster
 *
 * @author David Liu
 */
public class ReadWriteClusterIntegrationTest {

    private static TestScheduler testScheduler;
    private static EmbeddedEurekaCluster eurekaCluster;
    private static EurekaClient eurekaClient;
    private static InstanceInfo registeringInstanceInfo;

    @ClassRule
    public static final ExternalResource testResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            testScheduler = Schedulers.test();
            eurekaCluster = new EmbeddedEurekaCluster(3, 6, false);  // 3 write, 6 read, no bridge
            eurekaClient = Eureka.newClientBuilder(
                    ServerResolvers.just("localhost", 13200),
                    ServerResolvers.just("localhost", 13100))
                    .withCodec(EurekaTransports.Codec.Avro)
                    .build();
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
//            eurekaCluster.shutdown();  // FIXME: provide dummy metrics first before enable shutdown
        }
    };

    @Test
    public void ReadWriteClusterRegistrationTest() throws Exception {
        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();

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
                        notifications.add(notification);
                    }
                });

        eurekaClient.register(registeringInstanceInfo).subscribe();
//        eurekaClient.unregister(registeringInstanceInfo).subscribe();

        Thread.sleep(2000);

        assertThat(notifications.size(), equalTo(1));

        assertThat(notifications.get(0).getData().getApp(), equalTo(registeringInstanceInfo.getApp()));
        assertThat(notifications.get(0).getKind(), equalTo(ChangeNotification.Kind.Add));

//        assertThat(notifications.get(1).getData().getApp(), equalTo(registeringInstanceInfo.getApp()));
//        assertThat(notifications.get(1).getKind(), equalTo(ChangeNotification.Kind.Delete));
    }


}
