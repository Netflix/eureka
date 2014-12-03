package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.Subscriber;

@Singleton
public class EurekaServerStatusHandler {
    private final EurekaRegistryDataStream eurekaRegistryDataStream;

    @Inject
    public EurekaServerStatusHandler(EurekaRegistryDataStream eurekaRegistryDataStream) {
        this.eurekaRegistryDataStream = eurekaRegistryDataStream;
    }

    public Observable<Void> buildWebSocketResponse(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        eurekaRegistryDataStream.getStream().subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
            }

            @Override
            public void onError(Throwable e) {
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> instanceInfoChangeNotification) {
                final InstanceInfo instanceInfo = instanceInfoChangeNotification.getData();
                final String appId = instanceInfo.getApp();
                if (appId != null && appId.toLowerCase().startsWith("eureka")) {
                    final String respStr = "Instance - " + instanceInfo.getApp() + " :: " + instanceInfo.getHealthCheckUrls();
                    final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(respStr.getBytes());
                    webSocketConn.writeAndFlush(new TextWebSocketFrame(respByteBuf));
                }
            }
        });
        return Observable.empty();
    }

}
