package com.netflix.eureka2.integration.eureka1x;

import java.util.Collections;
import java.util.List;

import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1x.rest.Eureka1xConfiguration;
import com.netflix.eureka2.junit.categories.IntegrationTest;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.junit.resources.EurekaDeploymentResource;
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
public class Eureka1xRestApiIntegrationTest {

    private static final String EUREKA1_CLIENT_FILE = "sample-eureka1-client.properties";
    private static final String MY_APP_NAME = "myapp";
    private static final long TIMEOUT_MS = 60000;

    @Rule
    public final EurekaDeploymentResource deploymentResource = new EurekaDeploymentResource(1, 0);

    @Before
    public void setUp() throws Exception {
        // TODO Via system property until pluggable components configuration is more flexible
        System.setProperty(Eureka1xConfiguration.REFRESH_INTERVAL_KEY, "1");
    }

    @Test
    public void testFullFetch() throws Exception {
        final DiscoveryClient discoveryClient = createDefaultDiscoveryClient();

        Application app = await(new Func0<Application>() {
            @Override
            public Application call() {
                Applications applications = discoveryClient.getApplications();
                return applications.getRegisteredApplications().get(0);
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
        final com.netflix.eureka2.registry.instance.InstanceInfo instanceInfo = SampleInstanceInfo.WebServer.build();
        deploymentResource.connectToWriteCluster()
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
        return createDiscoveryClient(MY_APP_NAME, httpServerPort);
    }

    /**
     * Create discovery client that connects to this server, and registers with the given
     * application name. All configuration parameters are provided directly, however
     */
    public DiscoveryClient createDiscoveryClient(String appName, final int serverPort) {
        System.setProperty("eureka.client.props", EUREKA1_CLIENT_FILE);

        LeaseInfo leaseInfo = LeaseInfo.Builder.newBuilder()
                .setRenewalIntervalInSecs(1)
                .build();

        DataCenterInfo dataCenterInfo = new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        };

        Builder builder = Builder.newBuilder();
        builder.setAppName(appName);
        builder.setAppGroupName(appName);
        builder.setHostName(appName + ".host");
        builder.setIPAddr("127.0.0.1");
        builder.setDataCenterInfo(dataCenterInfo);
        builder.setLeaseInfo(leaseInfo);
        InstanceInfo instanceInfo = builder.build();

        DefaultEurekaClientConfig config = new DefaultEurekaClientConfig() {
            @Override
            public List<String> getEurekaServerServiceUrls(String myZone) {
                return Collections.singletonList("http://localhost:" + serverPort + "/eureka1x/v2/");
            }

            @Override
            public int getRegistryFetchIntervalSeconds() {
                return 1;
            }
        };
        return new DiscoveryClient(instanceInfo, config);
    }
}
