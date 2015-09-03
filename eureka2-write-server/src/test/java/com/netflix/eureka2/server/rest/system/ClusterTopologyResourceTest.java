package com.netflix.eureka2.server.rest.system;

import javax.ws.rs.core.MediaType;

import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.rxnetty.HttpResponseUtils;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.service.EurekaWriteServerSelfInfoResolver;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static com.netflix.eureka2.server.rest.system.ClusterTopologyResource.PATH_CLUSTER_TOPOLOGY;
import static com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver.META_EUREKA_WRITE_CLUSTER_ID;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class ClusterTopologyResourceTest {

    private static final InstanceInfo WEB_INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final InstanceInfo EUREKA_WRITE_INSTANCE = SampleInstanceInfo.EurekaWriteServer.build();
    private static final InstanceInfo EUREKA_READ_INSTANCE = SampleInstanceInfo.EurekaReadServer.builder()
            .withMetaData(META_EUREKA_WRITE_CLUSTER_ID, EUREKA_WRITE_INSTANCE.getVipAddress())
            .build();

    private final EurekaHttpServer httpServer = new EurekaHttpServer(anEurekaServerTransportConfig().withHttpPort(0).build());

    private final EurekaRegistry<InstanceInfo> registry = mock(EurekaRegistry.class);

    private final EurekaWriteServerSelfInfoResolver selfInfoResolver = mock(EurekaWriteServerSelfInfoResolver.class);

    private ClusterTopologyResource resource;

    @Before
    public void setUp() throws Exception {
        resource = new ClusterTopologyResource(selfInfoResolver, registry);
        httpServer.connectHttpEndpoint(PATH_CLUSTER_TOPOLOGY, resource);
        httpServer.start();

        when(selfInfoResolver.resolve()).thenReturn(Observable.just(EUREKA_WRITE_INSTANCE));

        when(registry.forSnapshot(Interests.forFullRegistry())).thenReturn(Observable.just(
                WEB_INSTANCE,
                EUREKA_WRITE_INSTANCE,
                EUREKA_READ_INSTANCE
        ));
    }

    @Test
    public void testAllEurekaDeploymentNodesAreReturned() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_CLUSTER_TOPOLOGY);
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getId()), is(false));
        assertThat(response.contains(EUREKA_WRITE_INSTANCE.getId()), is(true));
        assertThat(response.contains(EUREKA_READ_INSTANCE.getId()), is(true));
    }

    private String handleGetRequest(HttpClientRequest<ByteBuf> request) {
        return HttpResponseUtils.handleGetRequest(httpServer.serverPort(), request, MediaType.APPLICATION_JSON_TYPE);
    }
}