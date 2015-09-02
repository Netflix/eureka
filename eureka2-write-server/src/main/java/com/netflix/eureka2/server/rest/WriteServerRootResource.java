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

package com.netflix.eureka2.server.rest;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.http.JarResourcesRequestHandler;
import com.netflix.eureka2.server.rest.diagnostic.DiagnosticInstanceHoldersResource;
import com.netflix.eureka2.server.rest.system.ApplicationsResource;
import com.netflix.eureka2.server.rest.system.ClusterTopologyResource;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

import static com.netflix.eureka2.server.rest.diagnostic.DiagnosticInstanceHoldersResource.PATH_DIAGNOSTIC_ENTRYHOLDERS;
import static com.netflix.eureka2.server.rest.system.ApplicationsResource.PATH_APPLICATIONS;
import static com.netflix.eureka2.server.rest.system.ClusterTopologyResource.PATH_CLUSTER_TOPOLOGY;

/**
 * Root Eureka Write server REST resource.
 *
 * @author Tomasz Bak
 */
@Singleton
public class WriteServerRootResource implements RequestHandler<ByteBuf, ByteBuf> {

    private static final String PATH_WEB_CLIENT = "/ui";

    @Inject
    public WriteServerRootResource(EurekaHttpServer httpServer,
                                   ClusterTopologyResource clusterTopologyResource,
                                   ApplicationsResource applicationsResource,
                                   DiagnosticInstanceHoldersResource diagnosticInstanceHoldersResource) {
        JarResourcesRequestHandler jarHandler = new JarResourcesRequestHandler(
                PATH_WEB_CLIENT,
                "eureka2-write-ui-([\\d.]+)(-SNAPSHOT)?.jar",
                PATH_WEB_CLIENT + "/index.html"
        );
        httpServer.connectHttpEndpoint(PATH_WEB_CLIENT, jarHandler);
        httpServer.connectHttpEndpoint(PATH_CLUSTER_TOPOLOGY, clusterTopologyResource);
        httpServer.connectHttpEndpoint(PATH_APPLICATIONS, applicationsResource);
        httpServer.connectHttpEndpoint(PATH_DIAGNOSTIC_ENTRYHOLDERS, diagnosticInstanceHoldersResource);
        httpServer.connectHttpEndpoint("/api", this);
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        response.setStatus(HttpResponseStatus.SERVICE_UNAVAILABLE);
        return Observable.empty();
    }
}
