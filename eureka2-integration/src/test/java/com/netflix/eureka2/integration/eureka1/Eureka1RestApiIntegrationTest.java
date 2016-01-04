package com.netflix.eureka2.integration.eureka1;

import java.util.concurrent.TimeUnit;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.eureka1.rest.Eureka1RestApiReadModule;
import com.netflix.eureka2.eureka1.rest.Eureka1RestApiWriteModule;
import com.netflix.eureka2.eureka1.rest.config.Eureka1Configuration;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.junit.resources.Eureka1ClientResource;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources;
import com.netflix.eureka2.utils.functions.ChangeNotifications;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;
import rx.functions.Func0;
import rx.observers.TestSubscriber;

import static com.netflix.eureka2.eureka1.rest.config.Eureka1ConfigurationBean.anEureka1ConfigurationBean;
import static com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource.anEurekaDeploymentResource;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * The integration test uses Eureka 1.x client.
 *
 * @author Tomasz Bak
 */
@Category({IntegrationTest.class, LongRunningTest.class})
public class Eureka1RestApiIntegrationTest {

    private static final String EUREKA1_CLIENT_FILE = "sample-eureka1-client.properties";
    private static final String MY_APP_NAME = "myapp";
    private static final long TIMEOUT_MS = 60000;

    @Rule
    public final EurekaDeploymentResource deploymentResource = anEurekaDeploymentResource(1, 1)
            .withExtensionModule(ServerType.Write, Eureka1RestApiWriteModule.class)
            .withExtensionModule(ServerType.Read, Eureka1RestApiReadModule.class)
            .withConfiguration(
                    ServerType.Read,
                    Eureka1Configuration.class,
                    anEureka1ConfigurationBean().withCacheRefreshIntervalMs(1000).build()
            )
            .build();

    @Rule
    public final EurekaExternalResources externalResources = new EurekaExternalResources();

    @Before
    public void setUp() throws Exception {
        Interest<InstanceInfo> readVipInterest = Interests.forVips(deploymentResource.getEurekaDeployment().getReadCluster().getVip());

        ExtTestSubscriber<ChangeNotification<InstanceInfo>> extTestSubscriber = new ExtTestSubscriber<>();
        deploymentResource.interestClientToWriteCluster().forInterest(readVipInterest)
                .filter(ChangeNotifications.dataOnlyFilter())
                .subscribe(extTestSubscriber);

        // wait until the read server has registered with the write server
        ChangeNotification<InstanceInfo> readServer = extTestSubscriber.takeNext(10, TimeUnit.SECONDS);
        if (readServer == null) {
            fail("initialization fail, read server is not connected to the write server");
        }
    }

    @Test
    public void testFullFetch() throws Exception {
        final DiscoveryClient discoveryClient = createDefaultDiscoveryClient();

        Application app = await(new Func0<Application>() {
            @Override
            public Application call() {
                Applications applications = discoveryClient.getApplications();
                return applications.getRegisteredApplications().isEmpty() ? null : applications.getRegisteredApplications().get(0);
            }
        });
        assertThat(app, is(notNullValue()));
    }

    @Test
    public void testCacheRefresh() throws Exception {
        // Wait for first full fetch
        final DiscoveryClient discoveryClient = createDefaultDiscoveryClient();

        Application app = await(new Func0<Application>() {
            @Override
            public Application call() {
                Applications applications = discoveryClient.getApplications();
                return applications.getRegisteredApplications().isEmpty() ? null : applications.getRegisteredApplications().get(0);
            }
        });
        assertThat(app, is(notNullValue()));

        // Register a client with Eureka 2.x cluster
        TestSubscriber<EurekaRegistrationClient.RegistrationStatus> registrationSubscriber = new TestSubscriber<>();
        final InstanceInfo instanceInfo = SampleInstanceInfo.WebServer.build();
        deploymentResource.registrationClientToWriteCluster()
                .register(Observable.just(instanceInfo))
                .subscribe(registrationSubscriber);

        // Wait until the newly registry instance is uploaded to DiscoveryClient
        Application webApp = await(new Func0<Application>() {
            @Override
            public Application call() {
                Applications applications = discoveryClient.getApplications();
                return applications.getRegisteredApplications(instanceInfo.getApp());
            }
        });
        assertThat(webApp, is(notNullValue()));
    }

    private static <T> T await(Func0<T> fun) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        T result;
        do {
            result = fun.call();
            if (result == null) {
                Thread.sleep(1);
            }
        } while (result == null && System.currentTimeMillis() < deadline);
        return result;
    }

    private DiscoveryClient createDefaultDiscoveryClient() {
        int httpServerPort = deploymentResource.getEurekaDeployment().getWriteCluster().getServer(0).getHttpServerPort();
        Eureka1ClientResource clientResource = new Eureka1ClientResource(EUREKA1_CLIENT_FILE, MY_APP_NAME, httpServerPort);
        return externalResources.connect(clientResource).getEurekaClient();
    }
}
