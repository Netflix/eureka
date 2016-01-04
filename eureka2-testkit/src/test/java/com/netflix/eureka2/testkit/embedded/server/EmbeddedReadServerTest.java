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
import com.netflix.eureka2.testkit.junit.resources.ReadServerResource;
import com.netflix.eureka2.testkit.junit.resources.WriteServerResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import rx.Observable;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServerTest {

    public final WriteServerResource writeServerResource = new WriteServerResource();

    public final ReadServerResource readServerResource = new ReadServerResource(writeServerResource);

    @Rule
    public TestRule ruleChain = RuleChain.outerRule(writeServerResource).around(readServerResource);

    @Test(timeout = 10000)
    public void testInterestServices() throws Exception {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withServerResolver(ServerResolvers.fromHostname("localhost").withPort(writeServerResource.getServerPort()))
                .build();

        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withServerResolver(ServerResolvers.fromHostname("localhost").withPort(readServerResource.getServerPort()))
                .build();

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        Observable<RegistrationStatus> registrationRequest = registrationClient.register(Observable.just(instanceInfo));
        registrationRequest.subscribe();
        registrationRequest.take(1).toBlocking().lastOrDefault(null);

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