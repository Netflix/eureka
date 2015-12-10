package com.netflix.eureka2.integration.server.batching;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.ext.grpc.model.GrpcModelsInjector;
import com.netflix.eureka2.integration.EurekaDeploymentClients;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.protocol.StdProtocolModel;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.StdEurekaTransportFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.functions.Action1;

import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/**
 * @author Tomasz Bak
 */
@Category(IntegrationTest.class)
public class WriteServerNotificationBatchingTest {

    static {
        GrpcModelsInjector.injectGrpcModels();
        EurekaTransports.setTransportFactory(new StdEurekaTransportFactory());
        ProtocolModel.setDefaultModel(StdProtocolModel.getStdModel());
    }

    private static final int CLUSTER_SIZE = 50;

    @Rule
    public final EurekaDeploymentResource eurekaDeploymentResource = new EurekaDeploymentResource(2, 0);

    private EurekaDeploymentClients eurekaDeploymentClients;

    @Before
    public void setUp() throws Exception {
        eurekaDeploymentClients = new EurekaDeploymentClients(eurekaDeploymentResource.getEurekaDeployment());
    }

    @Test
    public void testWriteServerReturnsAvailableContentAsOneBatch() throws Exception {
        EurekaInterestClient subscriberClient = eurekaDeploymentResource.interestClientToWriteServer(0);

        InstanceInfo instanceTemplate = SampleInstanceInfo.WebServer.build();
        eurekaDeploymentClients.fillUpRegistry(CLUSTER_SIZE, instanceTemplate);

        LinkedHashSet<InstanceInfo> batch = subscriberClient.forInterest(Interests.forApplications(instanceTemplate.getApp()))
                .doOnNext(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        // Inject processing delay, to help expose potential batch marker races.
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException ignore) {
                        }
                    }
                })
                .compose(InterestFunctions.buffers())
                .compose(InterestFunctions.snapshots())
                .take(1)
                .timeout(30, TimeUnit.SECONDS)
                .toBlocking()
                .first();

        assertThat(batch.size(), is(equalTo(CLUSTER_SIZE)));
    }
}
