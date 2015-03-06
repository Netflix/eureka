package com.netflix.eureka2.eureka1.rest;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.eureka2.eureka1.rest.codec.Eureka1DataCodec.EncodingFormat;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxy;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxy.Result;
import com.netflix.eureka2.eureka1.rest.registry.Eureka1RegistryProxyImpl;
import com.netflix.eureka2.server.RegistrationClientProvider;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * @author Tomasz Bak
 */
public class Eureka1RegistrationRequestHandler extends AbstractEureka1RequestHandler {

    private final Eureka1RegistryProxy registryProxy;

    /* For testing */
    Eureka1RegistrationRequestHandler(Eureka1RegistryProxy registryProxy,
                                             EurekaHttpServer httpServer) {
        this.registryProxy = registryProxy;
        httpServer.connectHttpEndpoint(ROOT_PATH, this);
    }

    @Inject
    public Eureka1RegistrationRequestHandler(RegistrationClientProvider registrationClientProvider) {
        this.registryProxy = new Eureka1RegistryProxyImpl(registrationClientProvider.get(), Schedulers.io());
    }

    @Override
    public Observable<Void> dispatch(HttpServerRequest<ByteBuf> request,
                                     HttpServerResponse<ByteBuf> response) throws Exception {
        String path = request.getPath();
        if (request.getHttpMethod() == HttpMethod.POST) {
            EncodingFormat format = getRequestFormat(request);
            Matcher matcher = APP_INSTANCE_PATH_RE.matcher(path);
            if (matcher.matches()) {
                return registerApplication(matcher.group(1), format, request, response);
            }
        }
        if (request.getHttpMethod() == HttpMethod.PUT) {
            Matcher matcher = APP_INSTANCE_PATH_RE.matcher(path);
            if (matcher.matches()) {
                return handleHealthcheck(matcher.group(1), matcher.group(2), response);
            }
            matcher = APP_INSTANCE_META_PATH_RE.matcher(path);
            if (matcher.matches()) {
                return handleMetaAppend(matcher.group(1), matcher.group(2), request, response);
            }
        }
        if (request.getHttpMethod() == HttpMethod.DELETE) {
            Matcher matcher = APP_INSTANCE_PATH_RE.matcher(path);
            if (matcher.matches()) {
                return handleUnregister(matcher.group(1), matcher.group(2), response);
            }
        }
        return returnInvalidUrl(request, response);
    }

    private Observable<Void> registerApplication(final String appName,
                                                 EncodingFormat format,
                                                 HttpServerRequest<ByteBuf> request,
                                                 final HttpServerResponse<ByteBuf> response) throws IOException {
        return decodeBody(format, request, InstanceInfo.class).flatMap(new Func1<InstanceInfo, Observable<Void>>() {
            @Override
            public Observable<Void> call(InstanceInfo instanceInfo) {
                if (!appName.equals(instanceInfo.getAppName())) {
                    response.setStatus(HttpResponseStatus.BAD_REQUEST);
                    return Observable.empty();
                }
                registryProxy.register(instanceInfo);
                return Observable.empty();
            }
        });
    }

    private Observable<Void> handleMetaAppend(String appName,
                                              String instanceId,
                                              HttpServerRequest<ByteBuf> request,
                                              HttpServerResponse<ByteBuf> response) {
        Map<String, List<String>> queryParameters = request.getQueryParameters();
        Map<String, String> meta = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : queryParameters.entrySet()) {
            meta.put(entry.getKey(), entry.getValue().get(0));
        }
        Result result = registryProxy.appendMeta(appName, instanceId, meta);
        return returnWithStatus(response, result);
    }

    private Observable<Void> handleHealthcheck(String appName,
                                               String instanceId,
                                               HttpServerResponse<ByteBuf> response) {

        Result result = registryProxy.renewLease(appName, instanceId);
        return returnWithStatus(response, result);
    }

    private Observable<Void> handleUnregister(String appName, String instanceId, HttpServerResponse<ByteBuf> response) {
        Result result = registryProxy.unregister(appName, instanceId);
        return returnWithStatus(response, result);
    }

    private static Observable<Void> returnWithStatus(HttpServerResponse<ByteBuf> response, Result result) {
        switch (result) {
            case InvalidArguments:
                response.setStatus(HttpResponseStatus.BAD_REQUEST);
                break;
            case NotFound:
                response.setStatus(HttpResponseStatus.NOT_FOUND);
                break;
        }
        return Observable.empty();
    }
}
