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

import com.netflix.appinfo.EurekaAccept;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.converters.wrappers.CodecWrappers.JacksonJson;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClientImpl.EurekaJerseyClientBuilder;
import com.netflix.discovery.shared.transport.jersey.JerseyApplicationClient;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class SimpleEurekaHttpServerTest {

    private final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);

    private SimpleEurekaHttpServer httpServer;
    private JerseyApplicationClient eurekaHttpClient;

    private final InstanceInfoGenerator instanceGen = InstanceInfoGenerator.newBuilder(2, 1).build();

    @Before
    public void setUp() throws Exception {
        httpServer = new SimpleEurekaHttpServer(requestHandler);

        EurekaJerseyClient jerseyClient = new EurekaJerseyClientBuilder()
                .withClientName("test")
                .withMaxConnectionsPerHost(10)
                .withMaxTotalConnections(10)
                .withDecoder(JacksonJson.class.getSimpleName(), EurekaAccept.full.name())
                .withEncoder(JacksonJson.class.getSimpleName())
                .build();

        eurekaHttpClient = new JerseyApplicationClient(jerseyClient, "http://localhost:" + httpServer.getServerPort() + "/v2", false);
    }

    @After
    public void tearDown() throws Exception {
        httpServer.shutdown();
    }

    @Test
    public void testGetApplicationsRequest() throws Exception {
        Applications apps = instanceGen.toApplications();
        when(requestHandler.getApplications()).thenReturn(createResponse(apps));

        EurekaHttpResponse<Applications> httpResponse = eurekaHttpClient.getApplications();

        verifyResponseOkWithEntity(apps, httpResponse);
    }

    @Test
    public void testGetDeltaRequest() throws Exception {
        Applications delta = instanceGen.takeDelta(2);
        when(requestHandler.getDelta()).thenReturn(createResponse(delta));

        EurekaHttpResponse<Applications> httpResponse = eurekaHttpClient.getDelta();

        verifyResponseOkWithEntity(delta, httpResponse);
    }

    @Test
    public void testGetApplicationInstanceRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();

        when(requestHandler.getInstance(instance.getAppName(), instance.getId())).thenReturn(createResponse(instance));

        EurekaHttpResponse<InstanceInfo> httpResponse = eurekaHttpClient.getInstance(instance.getAppName(), instance.getId());

        verifyResponseOkWithEntity(instance, httpResponse);
    }

    @Test
    public void testRegisterRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();

        when(requestHandler.register(instance)).thenReturn(EurekaHttpResponse.<Void>responseWith(204));

        EurekaHttpResponse<Void> httpResponse = eurekaHttpClient.register(instance);

        assertThat(httpResponse.getStatusCode(), is(equalTo(204)));
    }

    @Test
    public void testCancelRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();

        when(requestHandler.cancel(instance.getAppName(), instance.getId())).thenReturn(EurekaHttpResponse.<Void>responseWith(200));

        EurekaHttpResponse<Void> httpResponse = eurekaHttpClient.cancel(instance.getAppName(), instance.getId());

        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testHeartbeatRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        InstanceInfo updated = new InstanceInfo.Builder(instance).setHostName("another.host").build();

        when(requestHandler.sendHeartBeat(instance.getAppName(), instance.getId(), null, null)).thenReturn(createResponse(updated));

        EurekaHttpResponse<InstanceInfo> httpResponse = eurekaHttpClient.sendHeartBeat(instance.getAppName(), instance.getId(), instance, null);

        verifyResponseOkWithEntity(updated, httpResponse);
    }

    @Test
    public void testStatusUpdateRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();

        when(requestHandler.statusUpdate(instance.getAppName(), instance.getId(), InstanceStatus.OUT_OF_SERVICE, null))
                .thenReturn(EurekaHttpResponse.<Void>responseWith(200));

        EurekaHttpResponse<Void> httpResponse = eurekaHttpClient.statusUpdate(instance.getAppName(), instance.getId(), InstanceStatus.OUT_OF_SERVICE, instance);

        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testStatusUpdateDeleteRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();

        when(requestHandler.deleteStatusOverride(instance.getAppName(), instance.getId(), null))
                .thenReturn(EurekaHttpResponse.<Void>responseWith(200));

        EurekaHttpResponse<Void> httpResponse = eurekaHttpClient.deleteStatusOverride(instance.getAppName(), instance.getId(), instance);

        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
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
        return EurekaHttpResponse.<T>anEurekaHttpResponse(200)
                .withEntity(entity)
                .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }
}