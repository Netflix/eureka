package com.netflix.eureka2.eureka1.rest;

import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.netflix.eureka2.eureka1.rest.codec.Eureka1DataCodec.EncodingFormat;
import com.netflix.eureka2.eureka1.rest.codec.XStreamEureka1DataCodec;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxy;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxy.Result;
import com.netflix.eureka2.model.instance.InstanceInfo;
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

import static com.netflix.eureka2.eureka1.rest.AbstractEureka1RequestHandler.ROOT_PATH;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfo;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Eureka1RegistrationRequestHandlerTest {

    private static final InstanceInfo V2_SAMPLE_INSTANCE = SampleInstanceInfo.WebServer.build();
    private static final com.netflix.appinfo.InstanceInfo V1_SAMPLE_INSTANCE =
            toEureka1xInstanceInfo(V2_SAMPLE_INSTANCE);

    private final EurekaHttpServer httpServer = new EurekaHttpServer(anEurekaServerTransportConfig().build());

    private final Eureka1RegistryProxy registryProxy = mock(Eureka1RegistryProxy.class);

    private final XStreamEureka1DataCodec codec = new XStreamEureka1DataCodec();
    private Eureka1RegistrationRequestHandler registrationResource;

    @Before
    public void setUp() throws Exception {
        registrationResource = new Eureka1RegistrationRequestHandler(registryProxy, httpServer);
        httpServer.connectHttpEndpoint(ROOT_PATH, registrationResource);
        httpServer.start();
    }

    @After
    public void tearDown() throws Exception {
        httpServer.stop();
    }

    @Test
    public void testRegistrationPost() throws Exception {
        String path = ROOT_PATH + "/apps/" + V1_SAMPLE_INSTANCE.getAppName();
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