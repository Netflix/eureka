/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.server.transport.http;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfo.Builder;
import com.netflix.eureka.rx.RxBlocking;
import com.netflix.eureka.server.service.EurekaServerService;
import com.netflix.eureka.server.transport.http.registration.RegistrationHttpRequestRouter;
import com.netflix.eureka.service.RegistrationChannel;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import rx.Observable;

import static com.netflix.eureka.registry.SampleInstanceInfo.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

/**
 * @author Tomasz Bak
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistrationHttpRequestRouterTest {

    private static final String BASE_URI = "/test";

    @Mock
    private EurekaServerService eurekaService;

    @Mock
    private HttpServerResponse<Object> response;

    private RegistrationHttpRequestRouter router;

    @Before
    public void setUp() throws Exception {
        router = new RegistrationHttpRequestRouter(eurekaService, BASE_URI);
    }

    @Test(timeout = 10000)
    public void testRegistrationAndUnregistration() throws Exception {
        InstanceInfo instanceInfo = DiscoveryServer.build();
        HttpServerRequest<Object> registerRequest = createHttpRequestMock(HttpMethod.POST, BASE_URI + "/apps", new Register(instanceInfo));
        HttpServerRequest<Object> unregisterRequest = createHttpRequestMock(HttpMethod.DELETE, BASE_URI + "/apps/" + instanceInfo.getId(), null);

        RegistrationChannel registrationChannel = createRegistrationChannelMock();
        registrationChannel.close();

        Observable<Void> result = router.route(registerRequest, response);
        RxBlocking.isCompleted(1, TimeUnit.SECONDS, result);

        // Now try to unregister
        result = router.route(unregisterRequest, response);
        RxBlocking.isCompleted(1, TimeUnit.SECONDS, result);

//        verify(eurekaService);
    }

    @Test(timeout = 10000)
    public void testUpdate() throws Exception {
        Builder instanceInfoBuilder = DiscoveryServer.builder();
        InstanceInfo firstVersion = instanceInfoBuilder.build();
        InstanceInfo secondVersion = instanceInfoBuilder.withApp("my_updated_app").build();

        HttpServerRequest<Object> registerRequest = createHttpRequestMock(HttpMethod.POST, BASE_URI + "/apps",
                new Register(firstVersion));
        HttpServerRequest<Object> updateRequest = createHttpRequestMock(HttpMethod.PUT, BASE_URI + "/apps/" + firstVersion.getId(),
                new Update(secondVersion));

        RegistrationChannel registrationChannel = createRegistrationChannelMock();
        when(registrationChannel.update(any(InstanceInfo.class))).thenReturn(Observable.<Void>empty());

        Observable<Void> result = router.route(registerRequest, response);
        RxBlocking.isCompleted(1, TimeUnit.SECONDS, result);

        // Now try to update
        result = router.route(updateRequest, response);
        RxBlocking.isCompleted(1, TimeUnit.SECONDS, result);

//        verify(eurekaService);
    }

    @Ignore
    @Test
    public void testHearbeat() throws Exception {
    }

    private HttpServerRequest<Object> createHttpRequestMock(HttpMethod method, String uri, Object command) {
        HttpServerRequest<Object> request = mock(HttpServerRequest.class);
        when(request.getHttpMethod()).thenReturn(method);
        when(request.getPath()).thenReturn(uri);
        if (command != null) {
            when(request.getContent()).thenReturn(Observable.just(command));
        }
        return request;
    }

    private RegistrationChannel createRegistrationChannelMock() {
        RegistrationChannel registrationChannel = mock(RegistrationChannel.class);
        when(registrationChannel.register(any(InstanceInfo.class))).thenReturn(Observable.<Void>empty());
        when(eurekaService.newRegistrationChannel()).thenReturn(registrationChannel);
        return registrationChannel;
    }
}