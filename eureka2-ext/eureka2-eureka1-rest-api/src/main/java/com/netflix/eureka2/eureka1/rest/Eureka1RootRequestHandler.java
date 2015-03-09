package com.netflix.eureka2.eureka1.rest;

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
public class Eureka1RootRequestHandler extends AbstractEureka1RequestHandler {

    private final Eureka1RegistrationRequestHandler registrationRequestHandler;
    private final Eureka1QueryRequestHandler queryRequestHandler;

    @Inject
    public Eureka1RootRequestHandler(EurekaHttpServer httpServer,
                                     Eureka1RegistrationRequestHandler registrationRequestHandler,
                                     Eureka1QueryRequestHandler queryRequestHandler) {
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
