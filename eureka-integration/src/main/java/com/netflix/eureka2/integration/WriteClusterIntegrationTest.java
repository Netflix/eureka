package com.netflix.eureka2.integration;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.testkit.embedded.EmbeddedEurekaCluster;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author David Liu
 */
public class WriteClusterIntegrationTest {

    private static AtomicLong uniqueId = new AtomicLong(0);

    private static EmbeddedEurekaCluster eurekaCluster;

    private EurekaClient eurekaClient;

    @ClassRule
    public static final ExternalResource embeddedCluster = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            eurekaCluster = new EmbeddedEurekaCluster(2, 0, false);  // 2 writes
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
        }

        @Override
        protected void after() {
            if (eurekaClient != null) {
                eurekaClient.close();
            }
        }
    };


    @Test
    public void WriteClusterReplicationFirstToSecondTest() throws Exception {
        eurekaClient = Eureka.newClientBuilder(
                ServerResolvers.just("localhost", 13111),  // read from second server
                ServerResolvers.just("localhost", 13100)   // register with first server
        ).build();

        InstanceInfo registeringInstanceInfo = new InstanceInfo.Builder()
                .withId("id#testClient")
                .withApp("WriteClusterReplicationFirstToSecondTest")
                .build();


        replicationServerOrderTest(eurekaClient, registeringInstanceInfo);
    }

    @Test
    public void WriteClusterReplicationSecondToFirstTest() throws Exception {
        eurekaClient = Eureka.newClientBuilder(
                ServerResolvers.just("localhost", 13101),  // read from first server
                ServerResolvers.just("localhost", 13110)   // register with second server
        ).build();

        InstanceInfo registeringInstanceInfo = new InstanceInfo.Builder()
                .withId("id#testClient")
                .withApp("WriteClusterReplicationSecondToFirstTest")
                .build();

        replicationServerOrderTest(eurekaClient, registeringInstanceInfo);
    }

    private void replicationServerOrderTest(EurekaClient client, InstanceInfo registeringInstanceInfo) throws Exception {

        final List<ChangeNotification<InstanceInfo>> notifications = new ArrayList<>();

        final CountDownLatch completionLatch = new CountDownLatch(1);
        client.forInterest(Interests.forApplications(registeringInstanceInfo.getApp()))
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
                        completionLatch.countDown();
                        notifications.add(notification);
                    }
                });

        client.register(registeringInstanceInfo).subscribe();
//        eurekaClient.unregister(registeringInstanceInfo).subscribe();

        completionLatch.await(5, TimeUnit.SECONDS);

        assertThat(notifications.size(), equalTo(1));

        assertThat(notifications.get(0).getData().getApp(), equalTo(registeringInstanceInfo.getApp()));
        assertThat(notifications.get(0).getKind(), equalTo(ChangeNotification.Kind.Add));

//        assertThat(notifications.get(1).getData().getApp(), equalTo(registeringInstanceInfo.getApp()));
//        assertThat(notifications.get(1).getKind(), equalTo(ChangeNotification.Kind.Delete));
    }

}
