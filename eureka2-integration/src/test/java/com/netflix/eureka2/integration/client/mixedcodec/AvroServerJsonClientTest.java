package com.netflix.eureka2.integration.client.mixedcodec;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class AvroServerJsonClientTest extends AbstractMixedCodecTest {

    private static final BasicEurekaTransportConfig jsonConfig = new BasicEurekaTransportConfig.Builder().withCodec(CodecType.Json).build();
    private static final BasicEurekaTransportConfig avroConfig = new BasicEurekaTransportConfig.Builder().withCodec(CodecType.Avro).build();

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