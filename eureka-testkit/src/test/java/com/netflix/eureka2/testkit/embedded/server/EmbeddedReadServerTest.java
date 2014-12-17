package com.netflix.eureka2.testkit.embedded.server;

import java.util.List;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.ReadServerResource;
import com.netflix.eureka2.testkit.junit.resources.WriteServerResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

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
        EurekaClient eurekaClient = new EurekaClientBuilder(
                ServerResolvers.just("localhost", readServerResource.getDiscoveryPort()),
                ServerResolvers.just("localhost", writeServerResource.getRegistrationPort())
        ).build();

        InstanceInfo instanceInfo = SampleInstanceInfo.DiscoveryServer.build();
        eurekaClient.register(instanceInfo).toBlocking().lastOrDefault(null);

        List<ChangeNotification<InstanceInfo>> notifications = eurekaClient
                .forInterest(Interests.forFullRegistry())
                .take(2)
                .toList()
                .toBlocking().single();

        assertThat(notifications.size(), is(equalTo(2)));
    }
}