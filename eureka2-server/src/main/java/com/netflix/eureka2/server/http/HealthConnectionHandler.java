package com.netflix.eureka2.server.http;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.netflix.eureka2.health.HealthStatusProvider;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.health.SubsystemDescriptor;
import com.netflix.eureka2.server.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.utils.Json;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ConnectionHandler;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class HealthConnectionHandler implements ConnectionHandler<WebSocketFrame, WebSocketFrame> {

    private final Observable<HealthStatusUpdate<?>> updateObservable;

    @Inject
    public HealthConnectionHandler(final EurekaHealthStatusAggregator aggregatedHealth,
                                   EurekaHttpServer httpServer) {
        updateObservable = aggregatedHealth.components()
                .flatMap(new Func1<List<HealthStatusProvider<?>>, Observable<HealthStatusUpdate<?>>>() {
                    @Override
                    public Observable<HealthStatusUpdate<?>> call(List<HealthStatusProvider<?>> healthStatusProviders) {
                        List<Observable<HealthStatusUpdate<?>>> updatesObservables = new ArrayList<>();
                        for (HealthStatusProvider<?> provider : healthStatusProviders) {
                            Observable statusUpdateObservable = provider.healthStatus();
                            updatesObservables.add(statusUpdateObservable);
                        }
                        Observable aggregatedObservable = aggregatedHealth.healthStatus();
                        updatesObservables.add(aggregatedObservable);
                        return Observable.merge(updatesObservables);
                    }
                });
        httpServer.connectWebSocketEndpoint("/health", this);
    }

    @Override
    public Observable<Void> handle(final ObservableConnection<WebSocketFrame, WebSocketFrame> connection) {
        // Ignore messages from client
        connection.getInput().subscribe();

        return updateObservable.flatMap(new Func1<HealthStatusUpdate<?>, Observable<Void>>() {
            @Override
            public Observable<Void> call(HealthStatusUpdate<?> healthStatusUpdate) {
                return connection.writeAndFlush(new TextWebSocketFrame(format(healthStatusUpdate)));
            }
        });
    }

    public static ByteBuf format(HealthStatusUpdate<?> healthStatusUpdate) {
        SubsystemDescriptor<?> descriptor = healthStatusUpdate.getDescriptor();
        Map<String, Object> descriptorMap = new HashMap<>();
        descriptorMap.put("className", descriptor.getSubsystemClass().getCanonicalName());
        descriptorMap.put("title", descriptor.getTitle());
        descriptorMap.put("description", descriptor.getDescription());

        Map<String, Object> rootMap = new HashMap<>();
        rootMap.put("status", healthStatusUpdate.getStatus().name());
        rootMap.put("descriptor", descriptorMap);
        return Json.toByteBufJson(rootMap);
    }
}
