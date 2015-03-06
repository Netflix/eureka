package com.netflix.eureka2.eureka1x.rest;

import javax.inject.Inject;

import com.netflix.eureka2.server.http.EurekaHttpServer;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class Eureka1xRootRequestHandler extends AbstractEureka1xRequestHandler {

    private final Eureka1xRegistrationRequestHandler registrationRequestHandler;
    private final Eureka1xQueryRequestHandler queryRequestHandler;

    @Inject
    public Eureka1xRootRequestHandler(EurekaHttpServer httpServer,
                                      Eureka1xRegistrationRequestHandler registrationRequestHandler,
                                      Eureka1xQueryRequestHandler queryRequestHandler) {
        this.registrationRequestHandler = registrationRequestHandler;
        this.queryRequestHandler = queryRequestHandler;
        httpServer.connectHttpEndpoint(ROOT_PATH, this);
    }

    @Override
    protected Observable<Void> dispatch(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) throws Exception {
        if (request.getHttpMethod() == HttpMethod.GET) {
            return queryRequestHandler.handle(request, response);
        }
        return registrationRequestHandler.handle(request, response);
    }
}
