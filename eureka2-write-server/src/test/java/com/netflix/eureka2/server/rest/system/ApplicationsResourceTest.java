package com.netflix.eureka2.server.rest.system;

import javax.ws.rs.core.MediaType;

import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rxnetty.HttpResponseUtils;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static com.netflix.eureka2.server.rest.system.ApplicationsResource.PATH_APPLICATIONS;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class ApplicationsResourceTest {

    private static final InstanceInfo WEB_INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final InstanceInfo BACKEND_INSTANCE = SampleInstanceInfo.Backend.build();

    private final EurekaHttpServer httpServer = new EurekaHttpServer(anEurekaServerTransportConfig().withHttpPort(0).build());

    private final SourcedEurekaRegistry<InstanceInfo> registry = mock(SourcedEurekaRegistry.class);

    private ApplicationsResource resource;

    @Before
    public void setUp() throws Exception {
        resource = new ApplicationsResource(registry);
        httpServer.connectHttpEndpoint(PATH_APPLICATIONS, resource);
        httpServer.start();

        when(registry.forSnapshot(Interests.forFullRegistry())).thenReturn(Observable.just(
                WEB_INSTANCE, BACKEND_INSTANCE
        ));
    }

    @Test
    public void testApplicationsGet() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_APPLICATIONS);
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getApp()), is(true));
        assertThat(response.contains(BACKEND_INSTANCE.getApp()), is(true));
    }

    private String handleGetRequest(HttpClientRequest<ByteBuf> request) {
        return HttpResponseUtils.handleGetRequest(httpServer.serverPort(), request, MediaType.APPLICATION_JSON_TYPE);
    }
}