package com.netflix.eureka2.eureka1x.rest;

import javax.ws.rs.core.MediaType;
import java.nio.charset.Charset;

import com.netflix.eureka2.registry.SourcedRegistryMockResource;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.config.EurekaServerConfig;
import com.netflix.eureka2.server.config.EurekaServerConfig.EurekaServerConfigBuilder;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.spi.ExtensionContext;
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
import org.junit.Rule;
import org.junit.Test;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import static com.netflix.eureka2.eureka1x.rest.AbstractEureka1xRequestHandler.ROOT_PATH;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Eureka1xQueryRequestHandlerTest {

    private static final int APPLICATION_CLUSTER_SIZE = 3;

    @Rule
    public final SourcedRegistryMockResource registryMockResource = new SourcedRegistryMockResource();

    private final EurekaServerConfig config = new EurekaServerConfigBuilder().withHttpPort(0).build();
    private final EurekaHttpServer httpServer = new EurekaHttpServer(config);
    private final ExtensionContext context = mock(ExtensionContext.class);
    private Eureka1xQueryRequestHandler queryResource;
    private String webAppName;
    private String backendAppName;

    @Before
    public void setUp() throws Exception {
        when(context.getLocalRegistry()).thenReturn(registryMockResource.registry());
        queryResource = new Eureka1xQueryRequestHandler(new Eureka1xConfiguration(), context);
        httpServer.connectHttpEndpoint(ROOT_PATH, queryResource);
        httpServer.start();
    }

    @After
    public void tearDown() throws Exception {
        httpServer.stop();
    }

    @Test
    public void testGetAllApplicationsInJson() throws Exception {
        doTestGetAllApplications(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetAllApplicationsInXml() throws Exception {
        doTestGetAllApplications(MediaType.APPLICATION_XML_TYPE);
    }

    private void doTestGetAllApplications(MediaType mediaType) {
        webAppName = registryMockResource.uploadClusterBatchToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);

        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, ROOT_PATH + "/apps");
        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("applications"), is(true));
    }

    @Test
    public void testGetApplicationsWithVipInJson() throws Exception {
        doTestGetApplicationsWithVip(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetApplicationsWithVipInXml() throws Exception {
        doTestGetApplicationsWithVip(MediaType.APPLICATION_XML_TYPE);
    }

    private void doTestGetApplicationsWithVip(MediaType mediaType) {
        InstanceInfo template = SampleInstanceInfo.WebServer.build();
        registryMockResource.uploadClusterToRegistry(template, APPLICATION_CLUSTER_SIZE);

        String path = ROOT_PATH + "/vips/" + template.getVipAddress();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("application"), is(true));
    }

    @Test
    public void testGetApplicationsWithSecureVipInJson() throws Exception {
        doTestGetApplicationsWithSecureVip(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetApplicationsWithSecureVipInXml() throws Exception {
        doTestGetApplicationsWithSecureVip(MediaType.APPLICATION_XML_TYPE);
    }

    private void doTestGetApplicationsWithSecureVip(MediaType mediaType) {
        InstanceInfo template = SampleInstanceInfo.WebServer.build();
        registryMockResource.uploadClusterToRegistry(template, APPLICATION_CLUSTER_SIZE);

        String path = ROOT_PATH + "/svips/" + template.getSecureVipAddress();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("application"), is(true));
    }

    @Test
    public void testDeltaGetReturnsNotImplementedError() throws Exception {
        String path = ROOT_PATH + "/apps/delta";
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);
        HttpClientResponse<ByteBuf> response = RxNetty.createHttpClient("localhost", httpServer.serverPort())
                .submit(request).toBlocking().first();
        assertThat(response.getStatus(), is(equalTo(HttpResponseStatus.NOT_IMPLEMENTED)));
    }

    @Test
    public void testGetApplicationInJson() throws Exception {
        doTestGetApplication(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetApplicationInXml() throws Exception {
        doTestGetApplication(MediaType.APPLICATION_XML_TYPE);
    }

    private void doTestGetApplication(MediaType mediaType) {
        webAppName = registryMockResource.uploadClusterToRegistry(SampleInstanceInfo.WebServer, APPLICATION_CLUSTER_SIZE);

        String path = ROOT_PATH + "/apps/" + webAppName;
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("application"), is(true));
    }

    @Test
    public void testGetByApplicationAndInstanceIdInJson() throws Exception {
        doTestGetByApplicationAndInstanceId(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetByApplicationAndInstanceIdInXml() throws Exception {
        doTestGetByApplicationAndInstanceId(MediaType.APPLICATION_XML_TYPE);
    }

    private void doTestGetByApplicationAndInstanceId(MediaType mediaType) {
        InstanceInfo webServerInfo = SampleInstanceInfo.WebServer.build();
        registryMockResource.uploadToRegistry(webServerInfo);

        String path = ROOT_PATH + "/apps/" + webServerInfo.getApp() + '/' + webServerInfo.getId();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("instance"), is(true));
    }

    @Test
    public void testGetByInstanceIdInJson() throws Exception {
        doTestGetByInstanceId(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetByInstanceIdInXml() throws Exception {
        doTestGetByInstanceId(MediaType.APPLICATION_XML_TYPE);
    }

    private void doTestGetByInstanceId(MediaType mediaType) {
        InstanceInfo webServerInfo = SampleInstanceInfo.WebServer.build();
        registryMockResource.uploadToRegistry(webServerInfo);

        String path = ROOT_PATH + "/instances/" + webServerInfo.getId();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("instance"), is(true));
    }

    private String handleGetRequest(HttpClientRequest<ByteBuf> request, final MediaType mediaType) {
        request.getHeaders().add(Names.ACCEPT, mediaType);
        return RxNetty.createHttpClient("localhost", httpServer.serverPort()).submit(request)
                .flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<String>>() {
                    @Override
                    public Observable<String> call(HttpClientResponse<ByteBuf> response) {
                        if (!response.getStatus().equals(HttpResponseStatus.OK)) {
                            return Observable.error(new Exception("invalid status code " + response.getStatus()));
                        }
                        String bodyContentType = response.getHeaders().get(Names.CONTENT_TYPE);
                        if (!mediaType.toString().equals(bodyContentType)) {
                            return Observable.error(new Exception("invalid Content-Type header in response " + bodyContentType));
                        }
                        return loadResponseBody(response);
                    }
                }).toBlocking().first();
    }

    private static Observable<String> loadResponseBody(HttpClientResponse<ByteBuf> response) {
        return response.getContent()
                .reduce(new StringBuilder(), new Func2<StringBuilder, ByteBuf, StringBuilder>() {
                    @Override
                    public StringBuilder call(StringBuilder accumulator, ByteBuf byteBuf) {
                        return accumulator.append(byteBuf.toString(Charset.defaultCharset()));
                    }
                }).map(new Func1<StringBuilder, String>() {
                    @Override
                    public String call(StringBuilder builder) {
                        return builder.toString();
                    }
                });
    }
}