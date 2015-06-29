package com.netflix.eureka2.server.service.bootstrap;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.Server;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedWriteServer;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import rx.schedulers.Schedulers;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * TODO As we miss transport abstraction yet, the test uses embedded cluster
 *
 * @author Tomasz Bak
 */
public class LightEurekaInterestClientTest {

    @Rule
    public EurekaDeploymentResource deploymentResource = new EurekaDeploymentResource(1, 0);

    private LightEurekaInterestClient lightInterestClient;

    @Before
    public void setUp() throws Exception {
        EmbeddedWriteServer writeServer = deploymentResource.getEurekaDeployment().getWriteCluster().getServer(0);
        Server server = new Server("localhost", writeServer.getInterestPort());
        lightInterestClient = new LightEurekaInterestClient(server, Schedulers.computation());
    }

    @Test(timeout = 30000)
    public void testFetchesAvailableRegistryContent() throws Exception {
        ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();
        lightInterestClient.forInterest(Interests.forFullRegistry()).subscribe(testSubscriber);

        ChangeNotification<InstanceInfo> notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(Kind.Add))); // This should be write node itself
        testSubscriber.assertOnCompleted(30, TimeUnit.SECONDS);
    }
}