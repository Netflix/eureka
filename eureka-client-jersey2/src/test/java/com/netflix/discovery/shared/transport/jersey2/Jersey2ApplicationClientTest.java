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

package com.netflix.discovery.shared.transport.jersey2;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.SimpleEurekaHttpServer;
import com.netflix.discovery.util.EurekaEntityComparators;
import com.netflix.discovery.util.InstanceInfoGenerator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * @author Tomasz Bak
 */
public class Jersey2ApplicationClientTest {

    private static final EurekaHttpClient requestHandler = mock(EurekaHttpClient.class);
    private static SimpleEurekaHttpServer eurekaHttpServer;

    private Jersey2ApplicationClient jersey2HttpClient;

    /**
     * Share server stub by all tests.
     */
    @BeforeClass
    public static void setUpClass() throws IOException {
        eurekaHttpServer = new SimpleEurekaHttpServer(requestHandler);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (eurekaHttpServer != null) {
            eurekaHttpServer.shutdown();
        }
    }

    @Before
    public void setUp() throws Exception {
        EurekaHttpClientFactory clientFactory = Jersey2ApplicationClientFactory.newBuilder().build();
        jersey2HttpClient = (Jersey2ApplicationClient) clientFactory.create(eurekaHttpServer.getServiceURI().toString());
    }

    @After
    public void tearDown() throws Exception {
        reset(requestHandler);
        if (jersey2HttpClient != null) {
            jersey2HttpClient.shutdown();
        }
    }

    @Test
    public void testRegistrationRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.register(instance)).thenReturn(EurekaHttpResponse.<Void>responseWith(204));

        EurekaHttpResponse<Void> httpResponse = jersey2HttpClient.register(instance);
        assertThat(httpResponse.getStatusCode(), is(equalTo(204)));
    }

    @Test
    public void testCancelRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.cancel(instance.getAppName(), instance.getId())).thenReturn(EurekaHttpResponse.<Void>responseWith(200));

        EurekaHttpResponse<Void> httpResponse = jersey2HttpClient.cancel(instance.getAppName(), instance.getId());
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testSendHeartBeat() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        InstanceInfo updated = new InstanceInfo.Builder(instance).setHostName("another.host").build();

        when(requestHandler.sendHeartBeat(instance.getAppName(), instance.getId(), null, null)).thenReturn(createResponse(updated));

        EurekaHttpResponse<InstanceInfo> httpResponse = jersey2HttpClient.sendHeartBeat(instance.getAppName(), instance.getId(), instance, null);
        verifyResponseOkWithEntity(updated, httpResponse);
    }

    @Test
    public void testStatusUpdateRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.statusUpdate(instance.getAppName(), instance.getId(), InstanceStatus.OUT_OF_SERVICE, null))
                .thenReturn(EurekaHttpResponse.<Void>responseWith(200));

        EurekaHttpResponse<Void> httpResponse = jersey2HttpClient.statusUpdate(instance.getAppName(), instance.getId(), InstanceStatus.OUT_OF_SERVICE, instance);
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testStatusUpdateDeleteRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.deleteStatusOverride(instance.getAppName(), instance.getId(), null))
                .thenReturn(EurekaHttpResponse.<Void>responseWith(200));

        EurekaHttpResponse<Void> httpResponse = jersey2HttpClient.deleteStatusOverride(instance.getAppName(), instance.getId(), instance);
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testGetAllApplications() throws Exception {
        Applications apps = InstanceInfoGenerator.newBuilder(2, 4).build().toApplications();
        when(requestHandler.getApplications()).thenReturn(createResponse(apps));

        EurekaHttpResponse<Applications> httpResponse = jersey2HttpClient.getApplications();
        assertThat(httpResponse.getStatusCode(), is(equalTo(200)));
    }

    @Test
    public void testGetDeltaRequest() throws Exception {
        Applications delta = InstanceInfoGenerator.newBuilder(2, 4).build().toApplications();
        when(requestHandler.getDelta()).thenReturn(createResponse(delta));

        EurekaHttpResponse<Applications> httpResponse = jersey2HttpClient.getDelta();
        verifyResponseOkWithEntity(delta, httpResponse);
    }

    @Test
    public void testGetApplicationInstanceRequest() throws Exception {
        InstanceInfo instance = InstanceInfoGenerator.takeOne();
        when(requestHandler.getInstance(instance.getAppName(), instance.getId())).thenReturn(createResponse(instance));

        EurekaHttpResponse<InstanceInfo> httpResponse = jersey2HttpClient.getInstance(instance.getAppName(), instance.getId());
        verifyResponseOkWithEntity(instance, httpResponse);
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