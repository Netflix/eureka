package com.netflix.eureka2.server.rest;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.server.http.EurekaHttpServer;
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

    @Inject
    public WriteServerRootResource(EurekaHttpServer httpServer,
                                   ClusterTopologyResource clusterTopologyResource,
                                   ApplicationsResource applicationsResource,
                                   DiagnosticInstanceHoldersResource diagnosticInstanceHoldersResource) {
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
