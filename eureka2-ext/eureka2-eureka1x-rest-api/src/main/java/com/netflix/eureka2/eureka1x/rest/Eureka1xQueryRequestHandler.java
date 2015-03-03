package com.netflix.eureka2.eureka1x.rest;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.regex.Matcher;

import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1x.rest.codec.Eureka1xDataCodec.EncodingFormat;
import com.netflix.eureka2.eureka1x.rest.query.Eureka2RegistryViewCache;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.spi.ExtensionContext;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

/**
 * This is implementation of Eureka 1.x REST API, as documented on
 * https://github.com/Netflix/eureka/wiki/Eureka-REST-operations.
 *
 * @author Tomasz Bak
 */
@Singleton
public class Eureka1xQueryRequestHandler extends AbstractEureka1xRequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1xQueryRequestHandler.class);

    private final Eureka2RegistryViewCache registryViewCache;

    @Inject
    public Eureka1xQueryRequestHandler(Eureka1xConfiguration config,
                                       EurekaHttpServer httpServer,
                                       ExtensionContext context) {
        super(context.getLocalRegistry());
        this.registryViewCache = new Eureka2RegistryViewCache(registry, config.getRefreshIntervalMs(), config.getQueryTimeout());
        httpServer.connectHttpEndpoint(ROOT_PATH, this);
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        String path = request.getPath();
        if (request.getHttpMethod() == HttpMethod.GET) {
            try {
                EncodingFormat format = getRequestFormat(request);
                boolean gzip = isGzipEncoding(request);

                Matcher matcher = APPS_PATH_RE.matcher(path);
                if (matcher.matches()) {
                    return appsGET(format, gzip, response);
                }
                matcher = APPS_DELTA_PATH_RE.matcher(path);
                if(matcher.matches()) {
                    return appGetDelta(response);
                }
                matcher = APP_PATH_RE.matcher(path);
                if (matcher.matches()) {
                    return appGET(matcher.group(1), format, gzip, response);
                }
                matcher = VIP_PATH_RE.matcher(path);
                if (matcher.matches()) {
                    return vipGET(matcher.group(1), format, gzip, response);
                }
                matcher = SECURE_VIP_PATH_RE.matcher(path);
                if (matcher.matches()) {
                    return secureVipGET(matcher.group(1), format, gzip, response);
                }
                matcher = APP_INSTANCE_PATH_RE.matcher(path);
                if (matcher.matches()) {
                    return instanceGetByAppAndInstanceId(matcher.group(1), matcher.group(2), format, gzip, response);
                }
                matcher = INSTANCE_PATH_RE.matcher(path);
                if (matcher.matches()) {
                    return instanceGetByInstanceId(matcher.group(1), format, gzip, response);
                }
            } catch (IOException e) {
                logger.error("Error during handling request GET " + path, e);
                return Observable.error(e);
            }
        }
        logger.info("Invalid request URL {} {}", request.getHttpMethod(), path);
        response.setStatus(HttpResponseStatus.NOT_FOUND);
        return Observable.empty();
    }

    private Observable<Void> appsGET(EncodingFormat format, boolean gzip, HttpServerResponse<ByteBuf> response) throws IOException {
        Applications applications = registryViewCache.findAllApplications();
        return encodeResponse(format, gzip, response, applications);
    }

    private Observable<Void> appGetDelta(HttpServerResponse<ByteBuf> response) {
        logger.info("Delta fetches not implemented");
        response.setStatus(HttpResponseStatus.NOT_IMPLEMENTED);
        return Observable.empty();
    }

    private Observable<Void> appGET(String appName, EncodingFormat format, boolean gzip, HttpServerResponse<ByteBuf> response) throws IOException {
        Application application = registryViewCache.findApplication(appName);
        return encodeResponse(format, gzip, response, application);
    }

    private Observable<Void> vipGET(String vip, EncodingFormat format, boolean gzip, HttpServerResponse<ByteBuf> response) throws IOException {
        Applications applications = registryViewCache.findApplicationsByVip(vip);
        return encodeResponse(format, gzip, response, applications);
    }

    private Observable<Void> secureVipGET(String secureVip, EncodingFormat format, boolean gzip, HttpServerResponse<ByteBuf> response) throws IOException {
        Applications applications = registryViewCache.findApplicationsBySecureVip(secureVip);
        return encodeResponse(format, gzip, response, applications);
    }

    private Observable<Void> instanceGetByAppAndInstanceId(String appName, String instanceId, EncodingFormat format,
                                                           boolean gzip, HttpServerResponse<ByteBuf> response) throws IOException {
        com.netflix.appinfo.InstanceInfo v1InstanceInfo = registryViewCache.findInstance(instanceId);
        if (v1InstanceInfo == null) {
            logger.info("Instance info with id {} not found", instanceId);
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return Observable.empty();
        }
        if (!appName.equalsIgnoreCase(v1InstanceInfo.getAppName())) {
            logger.info("Instance info with id {} is associated with application {}, not {}",
                    instanceId, v1InstanceInfo.getAppName(), appName);
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return Observable.empty();
        }
        return encodeResponse(format, gzip, response, v1InstanceInfo);
    }

    private Observable<Void> instanceGetByInstanceId(String instanceId, EncodingFormat format, boolean gzip,
                                                     HttpServerResponse<ByteBuf> response) throws IOException {
        com.netflix.appinfo.InstanceInfo v1InstanceInfo = registryViewCache.findInstance(instanceId);
        if (v1InstanceInfo == null) {
            logger.info("Instance info with id {} not found", instanceId);
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return Observable.empty();
        }
        return encodeResponse(format, gzip, response, v1InstanceInfo);
    }

    private static EncodingFormat getRequestFormat(HttpServerRequest<ByteBuf> request) throws IOException {
        String acceptHeader = request.getHeaders().get(Names.ACCEPT);
        if (acceptHeader == null) {
            return EncodingFormat.Json; // Default to JSON if nothing specified
        }
        MediaType mediaType;
        try {
            mediaType = MediaType.valueOf(acceptHeader);
            if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE)) {
                return EncodingFormat.Json;
            }
            if (mediaType.equals(MediaType.APPLICATION_XML_TYPE)) {
                return EncodingFormat.Xml;
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Unsupported content type " + acceptHeader, e);
        }
        throw new IOException("Only JSON and XML encodings are supported, and requested " + acceptHeader);
    }

}
