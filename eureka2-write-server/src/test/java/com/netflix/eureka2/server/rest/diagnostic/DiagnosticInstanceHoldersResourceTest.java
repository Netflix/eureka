package com.netflix.eureka2.server.rest.diagnostic;

import javax.ws.rs.core.MediaType;

import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.Source.Origin;
import com.netflix.eureka2.registry.data.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rxnetty.HttpResponseUtils;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;

import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static com.netflix.eureka2.server.rest.diagnostic.DiagnosticInstanceHoldersResource.PATH_DIAGNOSTIC_ENTRYHOLDERS;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class DiagnosticInstanceHoldersResourceTest {

    private final EurekaHttpServer httpServer = new EurekaHttpServer(anEurekaServerTransportConfig().withHttpPort(0).build());

    private static final InstanceInfo WEB_INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final InstanceInfo BACKEND_INSTANCE = SampleInstanceInfo.Backend.build();

    private static final MultiSourcedDataHolder<InstanceInfo> WEB_SERVER_HOLDER = buildOf(
            WEB_INSTANCE,
            new Source(Origin.LOCAL, "source1", 1),
            new Source(Origin.REPLICATED, "source2", 2)
    );
    private static final MultiSourcedDataHolder<InstanceInfo> BACKEND_SERVER_HOLDER = buildOf(
            BACKEND_INSTANCE,
            new Source(Origin.LOCAL, "source3", 1),
            new Source(Origin.REPLICATED, "source4", 2),
            new Source(Origin.REPLICATED, "source5", 3)
    );

    private DiagnosticInstanceHoldersResource resource;

    private final EurekaRegistry<InstanceInfo> registry = mock(EurekaRegistry.class);

    @Before
    public void setUp() throws Exception {
        resource = new DiagnosticInstanceHoldersResource(registry);
        httpServer.connectHttpEndpoint(PATH_DIAGNOSTIC_ENTRYHOLDERS, resource);
        httpServer.start();

        when(registry.getHolders())
                .thenAnswer(new Answer<Observable<MultiSourcedDataHolder<InstanceInfo>>>() {
                    @Override
                    public Observable<MultiSourcedDataHolder<InstanceInfo>> answer(InvocationOnMock invocation) throws Throwable {
                        return Observable.just(WEB_SERVER_HOLDER, BACKEND_SERVER_HOLDER);
                    }
                });
    }

    @After
    public void tearDown() throws Exception {
        httpServer.stop();
    }

    @Test
    public void testHoldersGet() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_DIAGNOSTIC_ENTRYHOLDERS);
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getId()), is(true));
        assertThat(response.contains(BACKEND_INSTANCE.getId()), is(true));
    }

    @Test
    public void testHoldersGetWithIdFilter() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_DIAGNOSTIC_ENTRYHOLDERS + "?id=" + WEB_INSTANCE.getId());
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getId()), is(true));
        assertThat(response.contains(BACKEND_INSTANCE.getId()), is(false));
    }

    @Test
    public void testHoldersGetWithAppFilter() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_DIAGNOSTIC_ENTRYHOLDERS + "?app=" + WEB_INSTANCE.getApp());
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getId()), is(true));
        assertThat(response.contains(BACKEND_INSTANCE.getId()), is(false));
    }

    @Test
    public void testHoldersGetWithSourceFilter() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_DIAGNOSTIC_ENTRYHOLDERS + "?source=" + WEB_SERVER_HOLDER.getSource().getName());
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getId()), is(true));
        assertThat(response.contains(BACKEND_INSTANCE.getId()), is(false));
    }

    @Test
    public void testHoldersGetWithCardinalityFilter() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_DIAGNOSTIC_ENTRYHOLDERS + "?cardinality=3");
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getId()), is(false));
        assertThat(response.contains(BACKEND_INSTANCE.getId()), is(true));
    }

    @Test
    public void testHolderByIdGet() throws Exception {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, PATH_DIAGNOSTIC_ENTRYHOLDERS + '/' + WEB_INSTANCE.getId());
        String response = handleGetRequest(request);
        assertThat(response.contains(WEB_INSTANCE.getId()), is(true));
    }

    private String handleGetRequest(HttpClientRequest<ByteBuf> request) {
        return HttpResponseUtils.handleGetRequest(httpServer.serverPort(), request, MediaType.APPLICATION_JSON_TYPE);
    }

    private static MultiSourcedDataHolder<InstanceInfo> buildOf(InstanceInfo instanceInfo, Source... sources) {
        MultiSourcedDataHolder<InstanceInfo> mockValue = mock(MultiSourcedDataHolder.class);
        when(mockValue.getId()).thenReturn(instanceInfo.getId());
        when(mockValue.getSource()).thenReturn(sources[0]);
        when(mockValue.getAllSources()).thenReturn(asList(sources));
        when(mockValue.get()).thenReturn(instanceInfo);
        when(mockValue.get(any(Source.class))).thenReturn(instanceInfo);
        return mockValue;
    }
}