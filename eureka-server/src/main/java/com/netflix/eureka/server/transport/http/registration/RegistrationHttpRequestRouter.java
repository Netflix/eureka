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

package com.netflix.eureka.server.transport.http.registration;

import com.google.inject.Inject;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.server.service.EurekaServerService;
import com.netflix.eureka.service.RegistrationChannel;
import com.netflix.karyon.transport.http.HttpRequestRouter;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;
import rx.functions.Func1;

import java.util.concurrent.ConcurrentHashMap;

/**
 * FIXME Implement heartbeat mechanism.
 *
 * @author Tomasz Bak
 */
public class RegistrationHttpRequestRouter implements HttpRequestRouter<Object, Object> {

    private final String appsBaseURI;
    private final EurekaServerService eurekaServerService;

    private final ConcurrentHashMap<String, RegistrationChannel> activeRegistrationChannels = new ConcurrentHashMap<String, RegistrationChannel>();

    @Inject
    public RegistrationHttpRequestRouter(EurekaServerService eurekaServerService, String baseURI) {
        this.eurekaServerService = eurekaServerService;
        this.appsBaseURI = baseURI + "/apps";
    }

    @Override
    public Observable<Void> route(HttpServerRequest<Object> request, HttpServerResponse<Object> response) {
        String path = request.getPath();
        if (!path.startsWith(appsBaseURI)) {
            return notFound(response);
        }

        // POST /apps
        HttpMethod httpMethod = request.getHttpMethod();
        if (path.length() == appsBaseURI.length()) {
            if (httpMethod.equals(HttpMethod.POST)) {
                return handleRegistrationPost(request, response);
            }
            return notFound(response);
        }

        // Must be /apps/...
        if (path.charAt(appsBaseURI.length()) != '/' || path.length() < appsBaseURI.length() + 2) {
            return notFound(response);
        }

        String instanceId = path.substring(appsBaseURI.length() + 1);

        if (httpMethod.equals(HttpMethod.GET)) {
            return handleInstanceGet(instanceId, response);
        }
        if (httpMethod.equals(HttpMethod.PUT)) {
            return handleInstanceUpdate(instanceId, request, response);
        }
        if (httpMethod.equals(HttpMethod.DELETE)) {
            return handleInstanceDelete(instanceId);
        }

        return notFound(response);
    }

    private Observable<Void> handleRegistrationPost(HttpServerRequest<Object> request, final HttpServerResponse<Object> response) {
        return request.getContent().flatMap(new Func1<Object, Observable<Void>>() {
            @Override
            public Observable<Void> call(Object o) {
                if (!(o instanceof Register)) {
                    response.setStatus(HttpResponseStatus.BAD_REQUEST);
                    return Observable.empty();
                }
                InstanceInfo instanceInfo = ((Register) o).getInstanceInfo();
                if (activeRegistrationChannels.contains(instanceInfo.getId())) {
                    activeRegistrationChannels.get(instanceInfo.getId()).close();
                }
                RegistrationChannel registrationChannel = eurekaServerService.newRegistrationChannel();
                activeRegistrationChannels.put(instanceInfo.getId(), registrationChannel);
                return registrationChannel.register(instanceInfo);
            }
        });
    }

    private Observable<Void> handleInstanceGet(String instanceId, HttpServerResponse<Object> response) {
        return Observable.error(new RuntimeException("not implemented yet"));
    }

    private Observable<Void> handleInstanceUpdate(final String instanceId, HttpServerRequest<Object> request, final HttpServerResponse<Object> response) {
        return request.getContent().flatMap(new Func1<Object, Observable<Void>>() {
            @Override
            public Observable<Void> call(Object o) {
                if (!(o instanceof Update)) {
                    response.setStatus(HttpResponseStatus.BAD_REQUEST);
                    return Observable.empty();
                }
                InstanceInfo instanceInfo = ((Update) o).getInstanceInfo();
                if (activeRegistrationChannels.containsKey(instanceId)) {
                    return activeRegistrationChannels.get(instanceId).update(instanceInfo);
                }
                // Instead of returning error, we silently do regular registration.
                RegistrationChannel registrationChannel = eurekaServerService.newRegistrationChannel();
                activeRegistrationChannels.put(instanceInfo.getId(), registrationChannel);
                return registrationChannel.register(instanceInfo);
            }
        });
    }

    private Observable<Void> handleInstanceDelete(String instanceId) {
        RegistrationChannel toRemove = activeRegistrationChannels.remove(instanceId);
        if (toRemove != null) {
            toRemove.close();
        }
        return Observable.empty();
    }

    private static Observable<Void> notFound(HttpServerResponse<Object> response) {
        response.setStatus(HttpResponseStatus.NOT_FOUND);
        return Observable.empty();
    }
}
