package com.netflix.eureka2.testkit.embedded.server;

import java.util.List;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClient.RegistrationStatus;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EmbeddedWriteServerTest {

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = anEurekaDeploymentResource(1, 0)
            .withNetworkRouter(true)
            .build();

    @Test(timeout = 10000)
    public void testRegistrationAndInterestServices() throws Exception {
        EmbeddedWriteServer writeServer = eurekaDeploymentResource.getEurekaDeployment().getWriteCluster().getServer(0);
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withServerResolver(ServerResolvers.fromHostname("localhost").withPort(writeServer.getServerPort()))
                .build();

        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withServerResolver(ServerResolvers.fromHostname("localhost").withPort(writeServer.getServerPort()))
                .build();

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        Observable<RegistrationStatus> request = registrationClient.register(Observable.just(instanceInfo));
        request.subscribe();
        request.take(1).toBlocking().lastOrDefault(null);

        List<ChangeNotification<InstanceInfo>> notifications = interestClient
                .forInterest(Interests.forFullRegistry())
                .take(2)
                .toList()
                .toBlocking().single();

        assertThat(notifications.size(), is(equalTo(2)));

        registrationClient.shutdown();
        interestClient.shutdown();
    }
}