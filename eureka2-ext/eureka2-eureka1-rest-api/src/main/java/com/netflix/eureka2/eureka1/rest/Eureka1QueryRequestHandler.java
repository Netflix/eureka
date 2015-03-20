package com.netflix.eureka2.eureka1.rest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.regex.Matcher;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.eureka2.eureka1.rest.codec.Eureka1DataCodec.EncodingFormat;
import com.netflix.eureka2.eureka1.rest.query.Eureka2RegistryViewCache;
import com.netflix.eureka2.server.spi.ExtensionContext;
import io.netty.buffer.ByteBuf;
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
public class Eureka1QueryRequestHandler extends AbstractEureka1RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1QueryRequestHandler.class);

    private final Eureka2RegistryViewCache registryViewCache;

    @Inject
    public Eureka1QueryRequestHandler(Eureka1Configuration config, ExtensionContext context) {
        this.registryViewCache = new Eureka2RegistryViewCache(context.getLocalRegistry(), config.getRefreshIntervalMs(), config.getQueryTimeout());
    }

    /* For testing */Eureka1QueryRequestHandler(Eureka2RegistryViewCache registryViewCache) {
        this.registryViewCache = registryViewCache;
    }

    @Override
    public Observable<Void> dispatch(HttpServerRequest<ByteBuf> request,
                                     HttpServerResponse<ByteBuf> response) throws Exception {
        String path = request.getPath();
        if (request.getHttpMethod() == HttpMethod.GET) {
            EncodingFormat format = getRequestFormat(request);
            boolean gzip = isGzipEncoding(request);

            Matcher matcher = APPS_PATH_RE.matcher(path);
            if (matcher.matches()) {
                return appsGET(format, gzip, response);
            }
            matcher = APPS_DELTA_PATH_RE.matcher(path);
            if (matcher.matches()) {
                return appGetDelta(format, gzip, response);
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
        }
        return returnInvalidUrl(request, response);
    }

    private Observable<Void> appsGET(EncodingFormat format, boolean gzip, HttpServerResponse<ByteBuf> response) throws IOException {
        Applications applications = registryViewCache.findAllApplications();
        return encodeResponse(format, gzip, response, applications);
    }

    private Observable<Void> appGetDelta(EncodingFormat format, boolean gzip, HttpServerResponse<ByteBuf> response) throws IOException {
        Applications applications = registryViewCache.findAllApplicationsDelta();
        return encodeResponse(format, gzip, response, applications);
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
        InstanceInfo v1InstanceInfo = registryViewCache.findInstance(instanceId);
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
        InstanceInfo v1InstanceInfo = registryViewCache.findInstance(instanceId);
        if (v1InstanceInfo == null) {
            logger.info("Instance info with id {} not found", instanceId);
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return Observable.empty();
        }
        return encodeResponse(format, gzip, response, v1InstanceInfo);
    }
}
