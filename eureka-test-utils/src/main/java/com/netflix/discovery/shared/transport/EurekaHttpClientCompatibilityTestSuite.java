/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.shared.transport;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.discovery.shared.transport.EurekaHttpResponse.anEurekaHttpResponse;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public abstract class EurekaHttpClientCompatibilityTestSuite {

    private static final String REMOTE_REGION = "us-east-1";

    private final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);

    private final List<EurekaHttpRequest> observedHttpRequests = new CopyOnWriteArrayList<>();
    private final EurekaTransportEventListener transportEventListener = new EurekaTransportEventListener() {
        @Override
        public void onHttpRequest(EurekaHttpRequest request) {
            observedHttpRequests.add(request);
        }
    };

    private SimpleEurekaHttpServer httpServer;

    protected EurekaHttpClientCompatibilityTestSuite() {
    }

    @Before
    public void setUp() throws Exception {
        httpServer = new SimpleEurekaHttpServer(requestHandler, transportEventListener);
    }

    @After
    public void tearDown() throws Exception {
        httpServer.shutdown();
    }

    protected abstract EurekaHttpClient getEurekaHttpClient(URI serviceURI);

    protected EurekaHttpClient getEurekaHttpClient() {
        return getEurekaHttpClient(getHttpServer().getServiceURI());
    }

    protected EurekaHttpClient getEurekaClientWithBasicAuthentication(String userName, String password) {
        URI serviceURI = UriBuilder.fromUri(getHttpServer().getServiceURI()).userInfo(userName + ':' + password).build();
        return getEurekaHttpClient(serviceURI);
    }

    protected SimpleEurekaHttpServer getHttpServer() {
        return httpServer;
    }

    @Test
    public void testRegisterRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.register(instance)).thenReturn(EurekaHttpResponse.status(204));

        EurekaHttpResponse<Void> httpResponse = getEurekaHttpClient().register(instance);
        assertThat(httpResponse.getStatusCode(), is(equalTo(204)));
    }

    @Test
    public void testCancelRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.cancel(instance.getAppName(), instance.getId())).thenReturn(EurekaHttpResponse.status(200));

        EurekaHttpResponse<Void> httpResponse = getEurekaHttpClient().cancel(instance.getAppName(), instance.getId());
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testHeartbeatRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        InstanceInfo updated = new InstanceInfo.Builder(instance).setHostName("another.host").build();
        when(requestHandler.sendHeartBeat(instance.getAppName(), instance.getId(), null, null)).thenReturn(createResponse(updated));

        EurekaHttpResponse<InstanceInfo> httpResponse = getEurekaHttpClient().sendHeartBeat(instance.getAppName(), instance.getId(), instance, null);
        verifyResponseOkWithEntity(updated, httpResponse);
    }

    @Test
    public void testStatusUpdateRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.statusUpdate(instance.getAppName(), instance.getId(), InstanceStatus.OUT_OF_SERVICE, null))
                .thenReturn(EurekaHttpResponse.status(200));

        EurekaHttpResponse<Void> httpResponse = getEurekaHttpClient().statusUpdate(instance.getAppName(), instance.getId(), InstanceStatus.OUT_OF_SERVICE, instance);
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testGetApplicationsRequest() throws Exception {
        Applications apps = InstanceInfoGenerator.newBuilder(2, 1).build().toApplications();
        when(requestHandler.getApplications()).thenReturn(createResponse(apps));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getApplications();
        verifyResponseOkWithEntity(apps, httpResponse);
    }

    @Test
    public void testGetApplicationsWithRemoteRegionRequest() throws Exception {
        Applications apps = InstanceInfoGenerator.newBuilder(2, 1).build().toApplications();
        when(requestHandler.getApplications(REMOTE_REGION)).thenReturn(createResponse(apps));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getApplications(REMOTE_REGION);
        verifyResponseOkWithEntity(apps, httpResponse);
    }

    @Test
    public void testGetDeltaRequest() throws Exception {
        Applications delta = InstanceInfoGenerator.newBuilder(2, 1).build().takeDelta(2);
        when(requestHandler.getDelta()).thenReturn(createResponse(delta));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getDelta();
        verifyResponseOkWithEntity(delta, httpResponse);
    }

    @Test
    public void testGetDeltaWithRemoteRegionRequest() throws Exception {
        Applications delta = InstanceInfoGenerator.newBuilder(2, 1).build().takeDelta(2);
        when(requestHandler.getDelta(REMOTE_REGION)).thenReturn(createResponse(delta));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getDelta(REMOTE_REGION);
        verifyResponseOkWithEntity(delta, httpResponse);
    }

    @Test
    public void testGetInstanceRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.getInstance(instance.getId())).thenReturn(createResponse(instance));

        EurekaHttpResponse<InstanceInfo> httpResponse = getEurekaHttpClient().getInstance(instance.getId());
        verifyResponseOkWithEntity(instance, httpResponse);
    }

    @Test
    public void testGetApplicationInstanceRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.getInstance(instance.getAppName(), instance.getId())).thenReturn(createResponse(instance));

        EurekaHttpResponse<InstanceInfo> httpResponse = getEurekaHttpClient().getInstance(instance.getAppName(), instance.getId());
        verifyResponseOkWithEntity(instance, httpResponse);
    }

    @Test
    public void testGetVipRequest() throws Exception {
        Applications vipApps = InstanceInfoGenerator.newBuilder(1, 2).build().toApplications();
        String vipAddress = vipApps.getRegisteredApplications().get(0).getInstances().get(0).getVIPAddress();
        when(requestHandler.getVip(vipAddress)).thenReturn(createResponse(vipApps));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getVip(vipAddress);
        verifyResponseOkWithEntity(vipApps, httpResponse);
    }

    @Test
    public void testGetVipWithRemoteRegionRequest() throws Exception {
        Applications vipApps = InstanceInfoGenerator.newBuilder(1, 2).build().toApplications();
        String vipAddress = vipApps.getRegisteredApplications().get(0).getInstances().get(0).getVIPAddress();
        when(requestHandler.getVip(vipAddress, REMOTE_REGION)).thenReturn(createResponse(vipApps));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getVip(vipAddress, REMOTE_REGION);
        verifyResponseOkWithEntity(vipApps, httpResponse);
    }

    @Test
    public void testGetSecureVipRequest() throws Exception {
        Applications vipApps = InstanceInfoGenerator.newBuilder(1, 2).build().toApplications();
        String secureVipAddress = vipApps.getRegisteredApplications().get(0).getInstances().get(0).getSecureVipAddress();
        when(requestHandler.getSecureVip(secureVipAddress)).thenReturn(createResponse(vipApps));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getSecureVip(secureVipAddress);
        verifyResponseOkWithEntity(vipApps, httpResponse);
    }

    @Test
    public void testGetSecureVipWithRemoteRegionRequest() throws Exception {
        Applications vipApps = InstanceInfoGenerator.newBuilder(1, 2).build().toApplications();
        String secureVipAddress = vipApps.getRegisteredApplications().get(0).getInstances().get(0).getSecureVipAddress();
        when(requestHandler.getSecureVip(secureVipAddress, REMOTE_REGION)).thenReturn(createResponse(vipApps));

        EurekaHttpResponse<Applications> httpResponse = getEurekaHttpClient().getSecureVip(secureVipAddress, REMOTE_REGION);
        verifyResponseOkWithEntity(vipApps, httpResponse);
    }

    @Test
    public void testStatusUpdateDeleteRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.deleteStatusOverride(instance.getAppName(), instance.getId(), null))
                .thenReturn(EurekaHttpResponse.status(200));

        EurekaHttpResponse<Void> httpResponse = getEurekaHttpClient().deleteStatusOverride(instance.getAppName(), instance.getId(), instance);
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testBasicAuthentication() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.register(instance)).thenReturn(EurekaHttpResponse.status(204));

        EurekaHttpResponse<Void> httpResponse = getEurekaClientWithBasicAuthentication("myuser", "mypassword").register(instance);
        assertThat(httpResponse.getStatusCode(), is(equalTo(204)));
        assertThat(observedHttpRequests.get(0).getHeaders().containsKey(HttpHeaders.AUTHORIZATION), is(true));
    }

    private static void verifyResponseOkWithEntity(Applications original, EurekaHttpResponse<Applications> httpResponse) {
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
        assertThat(httpResponse.getEntity(), is(notNullValue()));
        assertThat(EurekaEntityComparators.equal(httpResponse.getEntity(), original), is(true));
    }

    private static void verifyResponseOkWithEntity(InstanceInfo original, EurekaHttpResponse<InstanceInfo> httpResponse) {
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
        assertThat(httpResponse.getEntity(), is(notNullValue()));
        assertThat(EurekaEntityComparators.equal(httpResponse.getEntity(), original), is(true));
    }

    private static <T> EurekaHttpResponse<T> createResponse(T entity) {
        return anEurekaHttpResponse(200, entity).headers(HttpHeaders.CONTENT_TYPE, "application/json").build();
    }
}
