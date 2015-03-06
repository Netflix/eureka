package com.netflix.eureka2.eureka1.rest;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.ServiceEndpoint;
import com.netflix.eureka2.registry.instance.NetworkAddress.ProtocolType;
import com.netflix.eureka2.registry.selector.ServiceSelector;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.spi.ExtensionContext;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;

/**
 * @author Tomasz Bak
 */
public class Eureka1RedirectRequestHandler extends AbstractEureka1RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(Eureka1RedirectRequestHandler.class);

    private static final ServiceSelector HTTP_PUBLIC_SERVICE_SELECTOR = ServiceSelector.selectBy()
            .protocolType(ProtocolType.IPv4)
            .publicIp(true)
            .secure(false)
            .serviceLabel(com.netflix.eureka2.Names.EUREKA_HTTP);

    private static final ServiceSelector HTTP_PRIVATE_SERVICE_SELECTOR = ServiceSelector.selectBy()
            .protocolType(ProtocolType.IPv4)
            .publicIp(false)
            .secure(false)
            .serviceLabel(com.netflix.eureka2.Names.EUREKA_HTTP);

    private final ExtensionContext context;

    private Subscription subscription;
    private volatile List<InstanceInfo> readServers;
    private volatile int serverIndex;

    @Inject
    public Eureka1RedirectRequestHandler(ExtensionContext context,
                                         EurekaHttpServer httpServer) {
        this.context = context;
        httpServer.connectHttpEndpoint(ROOT_PATH, this);
    }

    @PostConstruct
    public void start() {
        subscription = context.getLocalRegistry()
                .forInterest(Interests.forVips(context.getConfig().getReadClusterVipAddress()))
                .compose(ChangeNotifications.<InstanceInfo>delineatedBuffers())
                .compose(ChangeNotifications.<InstanceInfo>snapshots())
                .subscribe(new Subscriber<Set<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        logger.info("Read server subscription completed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.info("Read server stream terminated with an error", e);
                    }

                    @Override
                    public void onNext(Set<InstanceInfo> instanceInfos) {
                        logger.info("Updating read server cluster to: {}", instanceInfos);
                        readServers = new ArrayList<InstanceInfo>(instanceInfos);
                    }
                });
    }

    @PreDestroy
    public void shutdown() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Override
    protected Observable<Void> dispatch(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) throws Exception {
        List<InstanceInfo> currentServers = readServers;
        if (currentServers != null && !currentServers.isEmpty()) {
            InstanceInfo next = currentServers.get(serverIndex % currentServers.size());
            serverIndex = (serverIndex + 1) % currentServers.size();

            return redirectTo(next, request, response);
        }
        response.setStatus(HttpResponseStatus.SERVICE_UNAVAILABLE);
        return Observable.empty();
    }

    private Observable<Void> redirectTo(InstanceInfo readServerInfo,
                                        HttpServerRequest<ByteBuf> request,
                                        HttpServerResponse<ByteBuf> response) {
        ServiceEndpoint serviceEndpoint = HTTP_PUBLIC_SERVICE_SELECTOR.returnServiceEndpoint(readServerInfo);
        String redirectHost = serviceEndpoint == null ? null : serviceEndpoint.getAddress().getHostName();
        if(redirectHost == null) {
            serviceEndpoint = HTTP_PRIVATE_SERVICE_SELECTOR.returnServiceEndpoint(readServerInfo);
            redirectHost = serviceEndpoint.getAddress().getIpAddress();
        }

        StringBuilder redirectBuilder = new StringBuilder("http://")
                .append(redirectHost)
                .append(':')
                .append(serviceEndpoint.getServicePort().getPort())
                .append(request.getPath());
        if (request.getQueryString() != null) {
            redirectBuilder.append('?').append(request.getQueryString());
        }


        response.getHeaders().add(Names.LOCATION, redirectBuilder.toString());
        response.setStatus(HttpResponseStatus.FOUND);
        return Observable.empty();
    }
}
