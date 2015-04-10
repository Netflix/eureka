package com.netflix.eureka2.integration.mixedcodec;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.transport.EurekaTransports;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class AvroServerJsonClientTest extends AbstractMixedCodecTest {

    private static final BasicEurekaTransportConfig jsonConfig = new BasicEurekaTransportConfig.Builder().withCodec(EurekaTransports.Codec.Json).build();
    private static final BasicEurekaTransportConfig avroConfig = new BasicEurekaTransportConfig.Builder().withCodec(EurekaTransports.Codec.Avro).build();

    @Rule
    public final EurekaDeploymentResource avroServers = new EurekaDeploymentResource(2, 0, avroConfig);

    @Test
    public void avroServerJsonClientTest() throws Exception {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withTransportConfig(jsonConfig)
                .withServerResolver(avroServers.getEurekaDeployment().getWriteCluster().registrationResolver())
                .build();

        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(jsonConfig)
                .withServerResolver(avroServers.getEurekaDeployment().getWriteCluster().interestResolver())
                .build();

        try {
            doTest(registrationClient, interestClient);
        } finally {
            interestClient.shutdown();
            registrationClient.shutdown();
        }
    }

}