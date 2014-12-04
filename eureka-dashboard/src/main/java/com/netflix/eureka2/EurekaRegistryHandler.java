package com.netflix.eureka2;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.List;

@Singleton
public class EurekaRegistryHandler {
    private static Logger log = LoggerFactory.getLogger(EurekaRegistryHandler.class);
    private final Gson gson;
    private RegistryStream registryStream;

    @Inject
    public EurekaRegistryHandler(RegistryStream registryStream) {
        this.registryStream = registryStream;
        gson = new Gson();
    }

    public Observable<Void> buildWebSocketResponse(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        registryStream.subscribe(new RegistryStream.RegistryStreamCallback() {
            @Override
            public boolean streamReceived(List<RegistryStream.RegistryItem> registryItems) {
                return RegistryStreamUtil.sendRegistryOverWebsocket(webSocketConn, registryItems, gson);
            }
        });

        return Observable.empty();
    }
}
