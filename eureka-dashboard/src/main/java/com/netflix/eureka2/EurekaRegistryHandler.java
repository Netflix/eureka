package com.netflix.eureka2;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

@Singleton
public class EurekaRegistryHandler {
    public static final String RESP_ERROR = "ERROR";
    public static final int BUFFER_TIME_SECONDS = 1;
    public static final int BUFFER_MAX_COUNT = 1000;
    private static Logger log = LoggerFactory.getLogger(EurekaRegistryHandler.class);
    private final EurekaRegistryDataStream eurekaRegistryDataStream;
    private final Gson gson;

    @Inject
    public EurekaRegistryHandler(EurekaRegistryDataStream eurekaRegistryDataStream) {
        this.eurekaRegistryDataStream = eurekaRegistryDataStream;
        gson = new GsonBuilder().registerTypeAdapter(Class.class, new SimpleGsonClassTypeAdapter()).create();
    }

    public Observable<Void> buildWebSocketResponse(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        subscribeToNewStream(webSocketConn);
        return Observable.empty();
    }

    private void subscribeToNewStream(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        //eurekaRegistryDataStream.getStream().buffer(BUFFER_TIME_SECONDS, TimeUnit.SECONDS, BUFFER_MAX_COUNT).subscribe(new Subscriber<List<ChangeNotification<InstanceInfo>>>() {
        /*
        eurekaRegistryDataStream.getStream().subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {

            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> instanceInfoChangeNotification) {
                final String jsonStr = "{ \"id\": \"" + instanceInfoChangeNotification.getData().getId() + "\"}";
                final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(jsonStr.getBytes());

                webSocketConn.writeAndFlush(new TextWebSocketFrame(respByteBuf));

            }
        });
        */

        eurekaRegistryDataStream.getStream().buffer(BUFFER_TIME_SECONDS, TimeUnit.SECONDS, BUFFER_MAX_COUNT).subscribe(new Subscriber<List<ChangeNotification<InstanceInfo>>>() {
            @Override
            public void onCompleted() {
                log.info("Eureka DATA Completed");
            }

            @Override
            public void onError(Throwable e) {
                log.error("Exception received in Eureka data stream.", e);
                sendError(webSocketConn);
            }

            @Override
            public void onNext(List<ChangeNotification<InstanceInfo>> changeNotifications) {
                try {
                    if (webSocketConn.getChannel().isOpen() && changeNotifications.size() > 0) {
                        final long s = System.currentTimeMillis();
                        final String jsonStr = gson.toJson(changeNotifications);
                        final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(jsonStr.getBytes());
                        final long f = System.currentTimeMillis();
                        System.out.println(String.format("Total serialization time %d millis ", (f - s)));

//                        final String jsonStr = "{ \"size\": \"" + changeNotifications.size() + "\"}";
//                        final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(jsonStr.getBytes());

                        webSocketConn.writeAndFlush(new TextWebSocketFrame(respByteBuf));
                    } else {
                        this.unsubscribe();
                    }
                } catch (Exception ex) {
                    log.error("Exception in onNext handler ", ex.getMessage());
                }
            }
        });

    }

    private void sendError(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(RESP_ERROR.getBytes());
        webSocketConn.writeAndFlush(new TextWebSocketFrame(respByteBuf));
    }

}
