package com.netflix.eureka2.simulator;

import javax.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.Builder;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.converters.EntityBodyConverter;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.functions.Func1;
import rx.functions.Func2;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * For some integration tests, and due to difficulties with mocking Eureka 1.x client API
 * we need real server for testing purposes. To make everything testable within single JVM
 * this class provides minimalistic simulator of Eureka 1.x REST API.
 * Instances of {@link DiscoveryClient} can be created with {@link Eureka1Server#createDiscoveryClient(String)}.
 * All client configuration is setup in code, and no external properties file is required.
 *
 * @author Tomasz Bak
 */
public class Eureka1Server {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1Server.class);

    public static final Pattern APPLICATIONS_PATH = Pattern.compile("/discovery/v2/apps/");
    public static final Pattern REGISTRATION_PATH = Pattern.compile("/discovery/v2/apps/([^/]+)");
    public static final Pattern INSTANCE_PATH = Pattern.compile("/discovery/v2/apps/([^/]+)/([^/]+)");

    private final HttpServer<ByteBuf, ByteBuf> server;
    private final Applications applications = new Applications();

    public Eureka1Server() {
        this.server = RxNetty.createHttpServer(0, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
                logger.info("Eureka request {} {}", request.getHttpMethod(), request.getPath());

                String path = request.getPath();
                Matcher matcher = APPLICATIONS_PATH.matcher(path);
                if (matcher.matches()) {
                    return applicationsResource(request, response);
                }
                matcher = REGISTRATION_PATH.matcher(path);
                if (matcher.matches()) {
                    return registrationResource(request, response, matcher.group(1));
                }
                matcher = INSTANCE_PATH.matcher(path);
                if (matcher.matches()) {
                    return instanceResource(request, response, matcher.group(1), matcher.group(2));
                }
                return notSupportedError(request, response);
            }
        });

        applications.setAppsHashCode("0");
    }

    public void start() {
        server.start();
    }

    public void stop() throws InterruptedException {
        server.shutdown();
    }

    public int getServerPort() {
        return server.getServerPort();
    }

    /**
     * Create discovery client that connects to this server, and registers with the given
     * application name. All configuration parameters are provided directly, however
     */
    public DiscoveryClient createDiscoveryClient(String appName) {
        LeaseInfo leaseInfo = LeaseInfo.Builder.newBuilder()
                .setRenewalIntervalInSecs(1)
                .build();

        DataCenterInfo dataCenterInfo = new DataCenterInfo() {
            @Override
            public Name getName() {
                return Name.MyOwn;
            }
        };

        Builder builder = Builder.newBuilder();
        builder.setAppName(appName);
        builder.setAppGroupName(appName);
        builder.setHostName(appName + ".host");
        builder.setIPAddr("127.0.0.1");
        builder.setDataCenterInfo(dataCenterInfo);
        builder.setLeaseInfo(leaseInfo);
        InstanceInfo instanceInfo = builder.build();

        ApplicationInfoManager manager = new ApplicationInfoManager(new MyDataCenterInstanceConfig(), instanceInfo);

        DefaultEurekaClientConfig config = new DefaultEurekaClientConfig() {
            @Override
            public List<String> getEurekaServerServiceUrls(String myZone) {
                return Collections.singletonList("http://localhost:" + getServerPort() + "/discovery/v2/");
            }
        };
        return new DiscoveryClient(manager, config);
    }

    public InstanceInfo findInstance(String instanceId) {
        for (Application app : applications.getRegisteredApplications()) {
            InstanceInfo instanceInfo = app.getByInstanceId(instanceId);
            if (instanceInfo != null) {
                return instanceInfo;
            }
        }
        return null;
    }

    public void assertContainsInstance(String appName, long timeout, TimeUnit timeUnit) throws InterruptedException {
        String expectedId = appName + ".host";
        long endTime = System.currentTimeMillis() + timeUnit.toMillis(timeout);
        InstanceInfo instanceInfo;
        while ((instanceInfo = findInstance(expectedId)) == null && System.currentTimeMillis() < endTime) {
            Thread.sleep(1);
        }
        assertThat(expectedId + " instance not found", instanceInfo, is(notNullValue()));
    }

    private Observable<Void> applicationsResource(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        return writeBody(response, applications);
    }

    private Observable<Void> registrationResource(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response,
                                                  final String appName) {
        if (request.getHttpMethod() != HttpMethod.POST) {
            return notSupportedError(request, response);
        }
        return readBody(request, InstanceInfo.class).map(new Func1<InstanceInfo, Void>() {
            @Override
            public Void call(InstanceInfo instanceInfo) {
                Application app = applications.getRegisteredApplications(instanceInfo.getAppName());
                if (app == null) {
                    applications.addApplication(app = new Application(appName));
                }
                app.addInstance(instanceInfo);
                return null;
            }
        });
    }

    private Observable<Void> instanceResource(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response,
                                              String appName, String instanceId) {
        Application app = applications.getRegisteredApplications(appName);
        if (app == null) {
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return Observable.empty();
        }

        if (request.getHttpMethod() == HttpMethod.PUT) {
            return Observable.empty();
        }
        if (request.getHttpMethod() == HttpMethod.DELETE) {
            InstanceInfo instanceInfo = app.getByInstanceId(instanceId);
            app.removeInstance(instanceInfo);
            return Observable.empty();
        }
        return notSupportedError(request, response);
    }

    private static <T> Observable<T> readBody(HttpServerRequest<ByteBuf> request, final Class<T> aClass) {
        return request.getContent().reduce(new StringBuilder(), new Func2<StringBuilder, ByteBuf, StringBuilder>() {
            @Override
            public StringBuilder call(StringBuilder acc, ByteBuf data) {
                return acc.append(data.toString(Charset.defaultCharset()));
            }
        }).flatMap(new Func1<StringBuilder, Observable<T>>() {
            @Override
            public Observable<T> call(StringBuilder bodyBuilder) {
                String body = bodyBuilder.toString();
                EntityBodyConverter converter = new EntityBodyConverter();
                T result;
                try {
                    result = (T) converter.read(new ByteArrayInputStream(body.getBytes()), aClass, MediaType.APPLICATION_JSON_TYPE);
                } catch (Exception e) {
                    return Observable.error(e);
                }
                return Observable.just(result);
            }
        });
    }

    private static <T> Observable<Void> writeBody(HttpServerResponse<ByteBuf> response, T value) {
        response.getHeaders().add(javax.ws.rs.core.HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON);

        EntityBodyConverter converter = new EntityBodyConverter();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            converter.write(value, os, MediaType.APPLICATION_JSON_TYPE);
        } catch (Exception e) {
            response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
            return Observable.error(e);
        }

        String body = os.toString();
        return response.writeStringAndFlush(body);
    }

    private static Observable<Void> notSupportedError(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
        return Observable.error(new Exception("Not supported " + request.getHttpMethod() + ' ' + request.getPath()));
    }
}
