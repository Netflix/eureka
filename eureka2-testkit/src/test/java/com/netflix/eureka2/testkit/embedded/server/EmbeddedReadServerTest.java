package com.netflix.eureka2.testkit.embedded.server;

import java.util.List;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClientBuilder;
import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.ReadServerResource;
import com.netflix.eureka2.testkit.junit.resources.WriteServerResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import rx.Observable;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServerTest {

    public final WriteServerResource writeServerResource = new WriteServerResource();

    public final ReadServerResource readServerResource = new ReadServerResource(writeServerResource);

    @Rule
    public TestRule ruleChain = RuleChain.outerRule(writeServerResource).around(readServerResource);

    @Test(timeout = 10000)
    public void testDiscoveryServices() throws Exception {
        EurekaRegistrationClient registrationClient = new EurekaRegistrationClientBuilder()
                .withServerResolver(ServerResolvers.withHostname("localhost").withPort(writeServerResource.getRegistrationPort()))
                .build();

        EurekaInterestClient interestClient = new EurekaInterestClientBuilder()
                .withServerResolver(ServerResolvers.withHostname("localhost").withPort(readServerResource.getDiscoveryPort()))
                .build();

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        RegistrationObservable registrationRequest = registrationClient.register(Observable.just(instanceInfo));
        registrationRequest.subscribe();
        registrationRequest.initialRegistrationResult().toBlocking().lastOrDefault(null);

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