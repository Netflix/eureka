package com.netflix.eureka2.eureka1.rest;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
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
import rx.Notification;
import rx.Notification.Kind;
import rx.Observable;
import rx.functions.Func1;

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
        this.registryViewCache = new Eureka2RegistryViewCache(context.getLocalRegistry(), config.getCacheRefreshIntervalMs());
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

    private Observable<Void> appsGET(final EncodingFormat format, final boolean gzip, final HttpServerResponse<ByteBuf> response) {
        return registryViewCache.findAllApplications().flatMap(new Func1<Applications, Observable<Void>>() {
            @Override
            public Observable<Void> call(Applications applications) {
                return encodeResponse(format, gzip, response, applications);
            }
        });
    }

    private Observable<Void> appGetDelta(final EncodingFormat format, final boolean gzip, final HttpServerResponse<ByteBuf> response) {
        return registryViewCache.findAllApplicationsDelta().flatMap(new Func1<Applications, Observable<Void>>() {
            @Override
            public Observable<Void> call(Applications applications) {
                return encodeResponse(format, gzip, response, applications);
            }
        });
    }

    private Observable<Void> appGET(String appName, final EncodingFormat format, final boolean gzip, final HttpServerResponse<ByteBuf> response) {
        return registryViewCache.findApplication(appName).flatMap(new Func1<Application, Observable<Void>>() {
            @Override
            public Observable<Void> call(Application application) {
                return encodeResponse(format, gzip, response, application);
            }
        });
    }

    private Observable<Void> vipGET(String vip, final EncodingFormat format, final boolean gzip, final HttpServerResponse<ByteBuf> response) {
        return registryViewCache.findApplicationsByVip(vip).flatMap(new Func1<Applications, Observable<Void>>() {
            @Override
            public Observable<Void> call(Applications applications) {
                return encodeResponse(format, gzip, response, applications);
            }
        });
    }

    private Observable<Void> secureVipGET(String secureVip, final EncodingFormat format, final boolean gzip, final HttpServerResponse<ByteBuf> response) {
        return registryViewCache.findApplicationsBySecureVip(secureVip).flatMap(new Func1<Applications, Observable<Void>>() {
            @Override
            public Observable<Void> call(Applications applications) {
                return encodeResponse(format, gzip, response, applications);
            }
        });
    }

    private Observable<Void> instanceGetByAppAndInstanceId(final String appName, final String instanceId, final EncodingFormat format,
                                                           final boolean gzip, final HttpServerResponse<ByteBuf> response) {
        return registryViewCache.findInstance(instanceId).materialize().toList().flatMap(
                new Func1<List<Notification<InstanceInfo>>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(List<Notification<InstanceInfo>> notifications) {
                        // If completed with error propagate it
                        Notification<InstanceInfo> lastNotification = notifications.get(notifications.size() - 1);
                        if (lastNotification.getKind() == Kind.OnError) {
                            return Observable.error(lastNotification.getThrowable());
                        }

                        // If onComplete only => instance info not found
                        if (notifications.size() == 1) {
                            logger.info("Instance info with id {} not found", instanceId);
                            response.setStatus(HttpResponseStatus.NOT_FOUND);
                            return Observable.empty();
                        }

                        // InstanceInfo object found
                        InstanceInfo v1InstanceInfo = notifications.get(0).getValue();

                        if (appName != null && !appName.equalsIgnoreCase(v1InstanceInfo.getAppName())) {
                            logger.info("Instance info with id {} is associated with application {}, not {}",
                                    instanceId, v1InstanceInfo.getAppName(), appName);
                            response.setStatus(HttpResponseStatus.NOT_FOUND);
                            return Observable.empty();
                        }

                        return encodeResponse(format, gzip, response, v1InstanceInfo);
                    }
                }
        );
    }

    private Observable<Void> instanceGetByInstanceId(String instanceId, EncodingFormat format, boolean gzip,
                                                     HttpServerResponse<ByteBuf> response) {
        return instanceGetByAppAndInstanceId(null, instanceId, format, gzip, response);
    }
}
