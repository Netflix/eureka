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

package com.netflix.eureka2.server.rest.system;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.Json;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

import static com.netflix.eureka2.server.rest.system.ApplicationDescriptor.anApplicationDescriptor;

/**
 * @author Tomasz Bak
 */
public class ApplicationsResource implements RequestHandler<ByteBuf, ByteBuf> {

    public static final String PATH_APPLICATIONS = "/api/system/applications";

    private static final Pattern APPLICATIONS_RE = Pattern.compile(PATH_APPLICATIONS);

    private final SourcedEurekaRegistry<InstanceInfo> registry;

    @Inject
    public ApplicationsResource(SourcedEurekaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        if (request.getHttpMethod() != HttpMethod.GET) {
            response.setStatus(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return Observable.empty();
        }
        String path = request.getPath();
        Matcher matcher = APPLICATIONS_RE.matcher(path);
        if (matcher.matches()) {
            return handleApplicationsRequest(request, response);
        }
        response.setStatus(HttpResponseStatus.NOT_FOUND);
        return Observable.empty();
    }

    private Observable<Void> handleApplicationsRequest(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        response.setStatus(HttpResponseStatus.OK);
        response.getHeaders().add(Names.CONTENT_TYPE, MediaType.APPLICATION_JSON);

        Map<String, ApplicationDescriptor.Builder> appBuilders = new HashMap<>();
        Iterator<InstanceInfo> it = registry.forSnapshot(Interests.forFullRegistry()).toBlocking().getIterator();
        while (it.hasNext()) {
            InstanceInfo instanceInfo = it.next();
            String appName = instanceInfo.getApp();
            if (appName != null) {
                ApplicationDescriptor.Builder builder = appBuilders.get(appName);
                if (builder == null) {
                    appBuilders.put(appName, builder = anApplicationDescriptor(appName));
                }
                builder.with(instanceInfo);
            }
        }
        List<ApplicationDescriptor> descriptors = new ArrayList<>(appBuilders.size());
        for (ApplicationDescriptor.Builder builder : appBuilders.values()) {
            descriptors.add(builder.build());
        }
        return response.writeStringAndFlush(Json.toStringJson(descriptors));
    }
}