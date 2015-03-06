package com.netflix.eureka2.eureka1x.rest;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.eureka1x.rest.codec.Eureka1xDataCodec.EncodingFormat;
import com.netflix.eureka2.eureka1x.rest.codec.XStreamEureka1xDataCodec;
import com.netflix.eureka2.eureka1x.rest.registry.Eureka1xRegistryProxy;
import com.netflix.eureka2.eureka1x.rest.registry.Eureka1xRegistryProxy.Result;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig.EurekaServerConfigBuilder;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;

import static com.netflix.eureka2.eureka1x.rest.AbstractEureka1xRequestHandler.ROOT_PATH;
import static com.netflix.eureka2.eureka1x.rest.model.Eureka1xDomainObjectModelMapper.EUREKA_1X_MAPPER;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Eureka1xRegistrationRequestHandlerTest {

    private static final InstanceInfo V2_SAMPLE_INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final com.netflix.appinfo.InstanceInfo V1_SAMPLE_INSTANCE =
            EUREKA_1X_MAPPER.toEureka1xInstanceInfo(V2_SAMPLE_INSTANCE);

    private final EurekaServerConfig config = new EurekaServerConfigBuilder().withHttpPort(0).build();
    private final EurekaHttpServer httpServer = new EurekaHttpServer(config);

    private final Eureka1xRegistryProxy registryProxy = mock(Eureka1xRegistryProxy.class);

    private final XStreamEureka1xDataCodec codec = new XStreamEureka1xDataCodec();
    private Eureka1xRegistrationRequestHandler registrationResource;

    @Before
    public void setUp() throws Exception {
        registrationResource = new Eureka1xRegistrationRequestHandler(registryProxy, httpServer);
        httpServer.connectHttpEndpoint(ROOT_PATH, registrationResource);
        httpServer.start();
    }

    @After
    public void tearDown() throws Exception {
        httpServer.stop();
    }

    @Test
    public void testRegistrationPost() throws Exception {
        String path = ROOT_PATH + "/apps/" + V1_SAMPLE_INSTANCE.getAppName() + '/' + V1_SAMPLE_INSTANCE.getId();
        handleRequestWithBody(HttpMethod.POST, path, V1_SAMPLE_INSTANCE, MediaType.APPLICATION_JSON);

        verify(registryProxy, times(1)).register(any(com.netflix.appinfo.InstanceInfo.class));
    }

    @Test
    public void testMetaUpdate() throws Exception {
        when(registryProxy.appendMeta(anyString(), anyString(), any(Map.class))).thenReturn(Result.Ok);

        String path = ROOT_PATH + "/apps/" + V1_SAMPLE_INSTANCE.getAppName() + '/' + V1_SAMPLE_INSTANCE.getId()
                + "/metadata?keyA=valueA";
        handleRequestWithoutBody(HttpMethod.PUT, path);

        Map<String, String> meta = new HashMap<>();
        meta.put("keyA", "valueA");
        verify(registryProxy, times(1)).appendMeta(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId(), meta);
    }

    @Test
    public void testHeartbeat() throws Exception {
        when(registryProxy.renewLease(anyString(), anyString())).thenReturn(Result.Ok);

        String path = ROOT_PATH + "/apps/" + V1_SAMPLE_INSTANCE.getAppName() + '/' + V1_SAMPLE_INSTANCE.getId();
        handleRequestWithoutBody(HttpMethod.PUT, path);

        verify(registryProxy, times(1)).renewLease(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());
    }

    @Test
    public void testUnregistration() throws Exception {
        when(registryProxy.unregister(anyString(), anyString())).thenReturn(Result.Ok);

        String path = ROOT_PATH + "/apps/" + V1_SAMPLE_INSTANCE.getAppName() + '/' + V1_SAMPLE_INSTANCE.getId();
        handleRequestWithoutBody(HttpMethod.DELETE, path);

        verify(registryProxy, times(1)).unregister(V1_SAMPLE_INSTANCE.getAppName(), V1_SAMPLE_INSTANCE.getId());
    }

    private void handleRequestWithoutBody(HttpMethod httpMethod, String path) {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(httpMethod, path);
        handleRequest(request);
    }

    private <T> void handleRequestWithBody(HttpMethod httpMethod, String path, T body, String mediaType) throws IOException {
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(httpMethod, path);
        request.getHeaders().add(Names.CONTENT_TYPE, mediaType);

        byte[] bodyBytes = codec.encode(body, EncodingFormat.Json, false);
        request.withContent(bodyBytes);

        handleRequest(request);
    }

    private void handleRequest(HttpClientRequest<ByteBuf> request) {
        RxNetty.createHttpClient("localhost", httpServer.serverPort()).submit(request)
                .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<String>>() {
                    @Override
                    public Observable<String> call(HttpClientResponse<ByteBuf> response) {
                        if (!response.getStatus().equals(HttpResponseStatus.OK)) {
                            return Observable.error(new Exception("invalid status code " + response.getStatus()));
                        }
                        return Observable.empty();
                    }
                }).toBlocking().firstOrDefault(null);
    }
}