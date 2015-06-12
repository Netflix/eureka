package com.netflix.eureka2.integration.client.mixedcodec;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.codec.CodecType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author David Liu
 */
@Category(IntegrationTest.class)
public class JsonServerAvroClientTest extends AbstractMixedCodecTest {

    private static final BasicEurekaTransportConfig jsonConfig = new BasicEurekaTransportConfig.Builder().withCodec(CodecType.Json).build();
    private static final BasicEurekaTransportConfig avroConfig = new BasicEurekaTransportConfig.Builder().withCodec(CodecType.Avro).build();

    @Rule
    public final EurekaDeploymentResource jsonServers = new EurekaDeploymentResource(2, 0, jsonConfig);

    @Test
    public void jsonServerAvroClientTest() throws Exception {
        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withTransportConfig(avroConfig)
                .withServerResolver(jsonServers.getEurekaDeployment().getWriteCluster().registrationResolver())
                .build();

        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withTransportConfig(avroConfig)
                .withServerResolver(jsonServers.getEurekaDeployment().getWriteCluster().interestResolver())
                .build();

        try {
            doTest(registrationClient, interestClient);
        } finally {
            interestClient.shutdown();
            registrationClient.shutdown();
        }
    }

}
