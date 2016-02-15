package com.netflix.discovery;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.converters.EntityBodyConverter;
import com.netflix.discovery.junit.resource.DiscoveryClientResource;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityFunctions;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.junit.MockServerRule;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author Tomasz Bak
 */
public class DiscoveryClientRedirectTest {

    static class MockClientHolder {
        MockServerClient client;
    }

    private final InstanceInfo myInstanceInfo = InstanceInfoGenerator.takeOne();

    @Rule
    public MockServerRule redirectServerMockRule = new MockServerRule(this);
    private MockServerClient redirectServerMockClient;

    private MockClientHolder targetServerMockClient = new MockClientHolder();
    @Rule
    public MockServerRule targetServerMockRule = new MockServerRule(targetServerMockClient);

    @Rule
    public DiscoveryClientResource registryFetchClientRule = DiscoveryClientResource.newBuilder()
            .withRegistration(false)
            .withRegistryFetch(true)
            .withPortResolver(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return redirectServerMockRule.getHttpPort();
                }
            })
            .withInstanceInfo(myInstanceInfo)
            .build();
    @Rule
    public DiscoveryClientResource registeringClientRule = DiscoveryClientResource.newBuilder()
            .withRegistration(true)
            .withRegistryFetch(false)
            .withPortResolver(new Callable<Integer>() {
                @Override
                public Integer call() throws Exception {
                    return redirectServerMockRule.getHttpPort();
                }
            })
            .withInstanceInfo(myInstanceInfo)
            .build();

    private String targetServerBaseUri;

    private final InstanceInfoGenerator dataGenerator = InstanceInfoGenerator.newBuilder(2, 1).withMetaData(true).build();

    @Before
    public void setUp() throws Exception {
        targetServerBaseUri = "http://localhost:" + targetServerMockRule.getHttpPort();
    }

    @After
    public void tearDown() {
        if (redirectServerMockClient != null) {
            redirectServerMockClient.reset();
        }

        if (targetServerMockClient.client != null) {
            targetServerMockClient.client.reset();
        }
    }

    @Test
    public void testClientQueryFollowsRedirectsAndPinsToTargetServer() throws Exception {
        Applications fullFetchApps = dataGenerator.takeDelta(1);
        String fullFetchJson = toJson(fullFetchApps);
        Applications deltaFetchApps = dataGenerator.takeDelta(1);
        String deltaFetchJson = toJson(deltaFetchApps);

        redirectServerMockClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/eureka/v2/apps/")
        ).respond(
                response()
                        .withStatusCode(302)
                        .withHeader(new Header("Location", targetServerBaseUri + "/eureka/v2/apps/"))
        );
        targetServerMockClient.client.when(
                request()
                        .withMethod("GET")
                        .withPath("/eureka/v2/apps/")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(new Header("Content-Type", "application/json"))
                        .withBody(fullFetchJson)
        );
        targetServerMockClient.client.when(
                request()
                        .withMethod("GET")
                        .withPath("/eureka/v2/apps/delta")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(new Header("Content-Type", "application/json"))
                        .withBody(deltaFetchJson)
        );

        final EurekaClient client = registryFetchClientRule.getClient();

        await(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<Application> applicationList = client.getApplications().getRegisteredApplications();
                return !applicationList.isEmpty() && applicationList.get(0).getInstances().size() == 2;
            }
        }, 1, TimeUnit.MINUTES);

        redirectServerMockClient.verify(request().withMethod("GET").withPath("/eureka/v2/apps/"), exactly(1));
        redirectServerMockClient.verify(request().withMethod("GET").withPath("/eureka/v2/apps/delta"), exactly(0));
        targetServerMockClient.client.verify(request().withMethod("GET").withPath("/eureka/v2/apps/"), exactly(1));
        targetServerMockClient.client.verify(request().withMethod("GET").withPath("/eureka/v2/apps/delta"), atLeast(1));
    }

    // There is an issue with using mock-server for this test case.  For now it is verified manually that it works.
    @Ignore
    @Test
    public void testClientRegistrationFollowsRedirectsAndPinsToTargetServer() throws Exception {
    }

    @Test
    public void testClientFallsBackToOriginalServerOnError() throws Exception {
        Applications fullFetchApps1 = dataGenerator.takeDelta(1);
        String fullFetchJson1 = toJson(fullFetchApps1);
        Applications fullFetchApps2 = EurekaEntityFunctions.mergeApplications(fullFetchApps1, dataGenerator.takeDelta(1));
        String fullFetchJson2 = toJson(fullFetchApps2);

        redirectServerMockClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/eureka/v2/apps/")
        ).respond(
                response()
                        .withStatusCode(302)
                        .withHeader(new Header("Location", targetServerBaseUri + "/eureka/v2/apps/"))
        );
        targetServerMockClient.client.when(
                request()
                        .withMethod("GET")
                        .withPath("/eureka/v2/apps/"),
                Times.exactly(1)
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(new Header("Content-Type", "application/json"))
                        .withBody(fullFetchJson1)
        );
        targetServerMockClient.client.when(
                request()
                        .withMethod("GET")
                        .withPath("/eureka/v2/apps/delta"),
                Times.exactly(1)
        ).respond(
                response()
                        .withStatusCode(500)
        );
        redirectServerMockClient.when(
                request()
                        .withMethod("GET")
                        .withPath("/eureka/v2/apps/delta")
        ).respond(
                response()
                        .withStatusCode(200)
                        .withHeader(new Header("Content-Type", "application/json"))
                        .withBody(fullFetchJson2)
        );

        final EurekaClient client = registryFetchClientRule.getClient();

        await(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                List<Application> applicationList = client.getApplications().getRegisteredApplications();
                return !applicationList.isEmpty() && applicationList.get(0).getInstances().size() == 2;
            }
        }, 1, TimeUnit.MINUTES);

        redirectServerMockClient.verify(request().withMethod("GET").withPath("/eureka/v2/apps/"), exactly(1));
        redirectServerMockClient.verify(request().withMethod("GET").withPath("/eureka/v2/apps/delta"), exactly(1));
        targetServerMockClient.client.verify(request().withMethod("GET").withPath("/eureka/v2/apps/"), exactly(1));
        targetServerMockClient.client.verify(request().withMethod("GET").withPath("/eureka/v2/apps/delta"), exactly(1));
    }

    private static String toJson(Applications applications) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        new EntityBodyConverter().write(applications, os, MediaType.APPLICATION_JSON_TYPE);
        os.close();
        return os.toString();
    }

    private static void await(Callable<Boolean> condition, long time, TimeUnit timeUnit) throws Exception {
        long timeout = System.currentTimeMillis() + timeUnit.toMillis(time);
        while (!condition.call()) {
            if (System.currentTimeMillis() >= timeout) {
                throw new TimeoutException();
            }
            Thread.sleep(100);
        }
    }
}
