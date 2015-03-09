package com.netflix.eureka2.integration.eureka1;

import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.rest.Eureka1Configuration;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.Eureka1ClientResource;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.Observable;
import rx.functions.Func0;
import rx.observers.TestSubscriber;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

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
    public final EurekaDeploymentResource deploymentResource = new EurekaDeploymentResource(1, 1);

    @Rule
    public final EurekaExternalResources externalResources = new EurekaExternalResources();

    @Before
    public void setUp() throws Exception {
        // TODO Via system property until pluggable components configuration is more flexible
        System.setProperty(Eureka1Configuration.REFRESH_INTERVAL_KEY, "1");
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
        TestSubscriber<Void> registrationSubscriber = new TestSubscriber<>();
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
