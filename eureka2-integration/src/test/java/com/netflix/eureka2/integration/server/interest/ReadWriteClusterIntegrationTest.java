package com.netflix.eureka2.integration.server.interest;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.ext.grpc.model.GrpcModelsInjector;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.protocol.StdProtocolModel;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.testkit.netrouter.NetworkLink;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.StdEurekaTransportFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;
import rx.Subscription;

import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class ReadWriteClusterIntegrationTest {

    static {
        GrpcModelsInjector.injectGrpcModels();
        EurekaTransports.setTransportFactory(new StdEurekaTransportFactory());
        ProtocolModel.setDefaultModel(StdProtocolModel.getStdModel());
    }

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource =
            anEurekaDeploymentResource(1, 1).withNetworkRouter(true).build();
    private EurekaDeployment eurekaDeployment;

    private EurekaRegistrationClient registrationClient;
    private EurekaInterestClient interestClient;
    private InstanceInfo registeringInfo;

    @Before
    public void setup() {
        eurekaDeployment = eurekaDeploymentResource.getEurekaDeployment();
        registrationClient = eurekaDeploymentResource.registrationClientToWriteCluster();
        interestClient = eurekaDeploymentResource.cannonicalInterestClient();
        registeringInfo = SampleInstanceInfo.CliServer.build();
    }

    @After
    public void tearDown() {
        registrationClient.shutdown();
        interestClient.shutdown();
    }

    @Test(timeout = 30000)
    public void testReadServerFetchesDataFromWriteServerRegistry() throws Exception {
        // Listen to interest stream updates
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();
        interestClient.forInterest(Interests.forApplications(registeringInfo.getApp()))
                .filter(dataOnlyFilter())
                .subscribe(notificationSubscriber);

        // Register
        Subscription subscription = registrationClient.register(Observable.just(registeringInfo)).subscribe();
        assertThat(notificationSubscriber.takeNextOrWait(), is(addChangeNotificationOf(registeringInfo)));

        // Unregister
        subscription.unsubscribe();
        assertThat(notificationSubscriber.takeNextOrWait(), is(deleteChangeNotificationOf(registeringInfo)));
    }

    @Test(timeout = 30000)
    public void testReadServerFailoversToAnotherWriteServerIfFirstOneCrashes() throws Exception {
        // Listen to interest stream updates
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();
        interestClient.forInterest(Interests.forApplications(registeringInfo.getApp()))
                .filter(dataOnlyFilter())
                .subscribe(notificationSubscriber);

        // Scale write cluster up so we have a node to failover
        eurekaDeployment.getWriteCluster().scaleUpByOne();

        // Disconnect network connection to the first write node
        NetworkLink networkLink = eurekaDeployment.getNetworkRouter()
                .getLinkTo(eurekaDeployment.getWriteCluster().getServer(0).getInterestPort());
        networkLink.disconnect(1, TimeUnit.SECONDS);

        // Register
        registrationClient.register(Observable.just(registeringInfo)).subscribe();
        assertThat(notificationSubscriber.takeNextOrWait(), is(addChangeNotificationOf(registeringInfo)));
    }
}
