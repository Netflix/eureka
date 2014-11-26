package com.netflix.eureka2;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.InstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;

@Singleton
public class EurekaRegistryHandler {
    private static Logger log = LoggerFactory.getLogger(EurekaRegistryHandler.class);

    private final EurekaRegistryDataStream eurekaRegistryDataStream;
    private final Gson gson;

    @Inject
    public EurekaRegistryHandler(EurekaRegistryDataStream eurekaRegistryDataStream) {
        this.eurekaRegistryDataStream = eurekaRegistryDataStream;
        gson = new Gson();
    }

    public Observable<Void> buildWebSocketResponse(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        eurekaRegistryDataStream.subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                log.info("Eureka DATA Completed");
            }

            @Override
            public void onError(Throwable e) {
                log.error("Exception received in Eureka data stream. Resubscribing...");
                eurekaRegistryDataStream.subscribe(this);
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> instanceInfoChangeNotification) {
                final String jsonStr = gson.toJson(instanceInfoChangeNotification);
                final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(jsonStr.getBytes());

                if (webSocketConn.getChannel().isOpen()) {
                    webSocketConn.writeAndFlush(new TextWebSocketFrame(respByteBuf));
                } else {
                    this.unsubscribe();
                }
            }
        });
        return Observable.empty();
    }
}
