package com.netflix.eureka2;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

@Singleton
public class EurekaRegistryHandler {
    private static Logger log = LoggerFactory.getLogger(EurekaRegistryHandler.class);
    private RegistryStream registryStream;

    @Inject
    public EurekaRegistryHandler(RegistryStream registryStream) {
        this.registryStream = registryStream;
    }

    public Observable<Void> buildWebSocketResponse(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        registryStream.subscribe(webSocketConn);
        return Observable.empty();
    }
}
