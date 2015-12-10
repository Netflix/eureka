package com.netflix.eureka2.integration.server.random;

import java.util.List;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.ext.grpc.model.GrpcModelsInjector;
import com.netflix.eureka2.integration.IntegrationTestClient;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.RandomTest;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.protocol.StdProtocolModel;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.StdEurekaTransportFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * @author David Liu
 */
@Category({IntegrationTest.class, RandomTest.class})
public class WriteClusterRandomLifecycleTest extends AbstractRandomLifecycleTest {

    static {
        GrpcModelsInjector.injectGrpcModels();
        EurekaTransports.setTransportFactory(new StdEurekaTransportFactory());
        ProtocolModel.setDefaultModel(StdProtocolModel.getStdModel());
    }

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(2, 0);

    @Test(timeout = 60000)
    public void writeServerRandomLifecycleTest() {
        final EurekaInterestClient readClient = eurekaDeploymentResource.interestClientToWriteServer(0);
        final EurekaRegistrationClient writeClient = eurekaDeploymentResource.registrationClientToWriteServer(1);

        IntegrationTestClient testClient = new IntegrationTestClient(readClient, writeClient);

        List<ChangeNotification<InstanceInfo>> expectedLifecycle = testClient.getExpectedLifecycle();
        List<ChangeNotification<InstanceInfo>> actualLifecycle = testClient.playLifecycle();

        assertLifecycles(expectedLifecycle, actualLifecycle);

        readClient.shutdown();
        writeClient.shutdown();
    }
}
