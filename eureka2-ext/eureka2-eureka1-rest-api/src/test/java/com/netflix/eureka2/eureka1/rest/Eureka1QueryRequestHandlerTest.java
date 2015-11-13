package com.netflix.eureka2.eureka1.rest;

import javax.ws.rs.core.MediaType;
import java.util.List;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.rest.query.Eureka2RegistryViewCache;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.rxnetty.HttpResponseUtils;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.client.HttpClientRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Observable;

import static com.netflix.eureka2.eureka1.rest.AbstractEureka1RequestHandler.ROOT_PATH;
import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfo;
import static com.netflix.eureka2.server.config.bean.EurekaServerTransportConfigBean.anEurekaServerTransportConfig;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Eureka1QueryRequestHandlerTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    private static final com.netflix.appinfo.InstanceInfo V1_INSTANCE_1;
    private static final com.netflix.appinfo.InstanceInfo V1_INSTANCE_2;
    private static final Application V1_APPLICATION_1;
    private static final Applications V1_APPLICATIONS;

    static {
        List<InstanceInfo> app1 = SampleInstanceInfo.WebServer.clusterOf(2);
        V1_INSTANCE_1 = toEureka1xInstanceInfo(app1.get(0));
        V1_INSTANCE_2 = toEureka1xInstanceInfo(app1.get(1));
        V1_APPLICATION_1 = new Application("WebServer");
        V1_APPLICATION_1.addInstance(V1_INSTANCE_1);
        V1_APPLICATION_1.addInstance(V1_INSTANCE_2);

        V1_APPLICATIONS = new Applications();
        V1_APPLICATIONS.addApplication(V1_APPLICATION_1);
        V1_APPLICATIONS.setAppsHashCode("test");
    }

    private final EurekaHttpServer httpServer = new EurekaHttpServer(anEurekaServerTransportConfig().build());
    private final Eureka2RegistryViewCache registryViewCache = mock(Eureka2RegistryViewCache.class);
    private Eureka1QueryRequestHandler queryResource;

    @Before
    public void setUp() throws Exception {
        queryResource = new Eureka1QueryRequestHandler(registryViewCache);
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

    @Test
    public void testGetApplicationsDeltaInJson() throws Exception {
        doTestGetApplicationsDelta(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetApplicationsDeltaInXml() throws Exception {
        doTestGetApplicationsDelta(MediaType.APPLICATION_XML_TYPE);
    }

    @Test
    public void testGetApplicationsWithVipInJson() throws Exception {
        doTestGetApplicationsWithVip(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetApplicationsWithVipInXml() throws Exception {
        doTestGetApplicationsWithVip(MediaType.APPLICATION_XML_TYPE);
    }

    @Test
    public void testGetApplicationsWithSecureVipInJson() throws Exception {
        doTestGetApplicationsWithSecureVip(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetApplicationsWithSecureVipInXml() throws Exception {
        doTestGetApplicationsWithSecureVip(MediaType.APPLICATION_XML_TYPE);
    }

    @Test
    public void testGetApplicationInJson() throws Exception {
        doTestGetApplication(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetApplicationInXml() throws Exception {
        doTestGetApplication(MediaType.APPLICATION_XML_TYPE);
    }

    @Test
    public void testGetByApplicationAndInstanceIdInJson() throws Exception {
        doTestGetByApplicationAndInstanceId(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetByApplicationAndInstanceIdInXml() throws Exception {
        doTestGetByApplicationAndInstanceId(MediaType.APPLICATION_XML_TYPE);
    }

    @Test
    public void testGetByInstanceIdInJson() throws Exception {
        doTestGetByInstanceId(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    public void testGetByInstanceIdInXml() throws Exception {
        doTestGetByInstanceId(MediaType.APPLICATION_XML_TYPE);
    }

    private void doTestGetAllApplications(MediaType mediaType) {
        when(registryViewCache.findAllApplications()).thenReturn(Observable.just(V1_APPLICATIONS));

        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, ROOT_PATH + "/apps");
        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("applications"), is(true));
    }

    private void doTestGetApplicationsDelta(MediaType mediaType) {
        when(registryViewCache.findAllApplicationsDelta()).thenReturn(Observable.just(V1_APPLICATIONS));

        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, ROOT_PATH + "/apps/delta");
        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("applications"), is(true));
    }

    private void doTestGetApplicationsWithSecureVip(MediaType mediaType) {
        when(registryViewCache.findApplicationsBySecureVip(V1_INSTANCE_1.getSecureVipAddress())).thenReturn(Observable.just(V1_APPLICATIONS));

        String path = ROOT_PATH + "/svips/" + V1_INSTANCE_1.getSecureVipAddress();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("application"), is(true));
    }

    private void doTestGetApplicationsWithVip(MediaType mediaType) {
        when(registryViewCache.findApplicationsByVip(V1_INSTANCE_1.getVIPAddress())).thenReturn(Observable.just(V1_APPLICATIONS));

        String path = ROOT_PATH + "/vips/" + V1_INSTANCE_1.getVIPAddress();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("application"), is(true));
    }

    private void doTestGetApplication(MediaType mediaType) {
        when(registryViewCache.findApplication(V1_APPLICATION_1.getName())).thenReturn(Observable.just(V1_APPLICATION_1));

        String path = ROOT_PATH + "/apps/" + V1_APPLICATION_1.getName();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("application"), is(true));
    }

    private void doTestGetByApplicationAndInstanceId(MediaType mediaType) {
        when(registryViewCache.findInstance(V1_INSTANCE_1.getId())).thenReturn(Observable.just(V1_INSTANCE_1));

        String path = ROOT_PATH + "/apps/" + V1_INSTANCE_1.getAppName() + '/' + V1_INSTANCE_1.getId();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("instance"), is(true));
    }

    private void doTestGetByInstanceId(MediaType mediaType) {
        when(registryViewCache.findInstance(V1_INSTANCE_1.getId())).thenReturn(Observable.just(V1_INSTANCE_1));

        String path = ROOT_PATH + "/instances/" + V1_INSTANCE_1.getId();
        HttpClientRequest<ByteBuf> request = HttpClientRequest.create(HttpMethod.GET, path);

        String response = handleGetRequest(request, mediaType);
        assertThat(response.contains("instance"), is(true));
    }

    private String handleGetRequest(HttpClientRequest<ByteBuf> request, final MediaType mediaType) {
        return HttpResponseUtils.handleGetRequest(httpServer.serverPort(), request, mediaType);
    }
}