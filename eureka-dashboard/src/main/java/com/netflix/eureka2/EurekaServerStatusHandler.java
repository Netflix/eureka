package com.netflix.eureka2;

import com.google.gson.Gson;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.netflix.eureka2.RegistryStream.RegistryItem;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class EurekaServerStatusHandler {

    private RegistryStream registryStream;
    private final Gson gson;

    @Inject
    public EurekaServerStatusHandler(RegistryStream registryStream) {
        this.registryStream = registryStream;
        gson = new Gson();
    }

    public Observable<Void> buildWebSocketResponse(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn) {
        registryStream.subscribe(new RegistryStream.RegistryStreamCallback() {
            @Override
            public boolean streamReceived(List<RegistryStream.RegistryItem> registryItems) {
                final List<RegistryItem> eurekaItems = filterEurekaItems(registryItems);
                return RegistryStreamUtil.sendRegistryOverWebsocket(webSocketConn, eurekaItems, gson);
            }
        });

        return Observable.empty();
    }

    private List<RegistryItem> filterEurekaItems(List<RegistryItem> registryItems) {
        List<RegistryItem> eurekaItems = new ArrayList<>();
        for (RegistryItem registryItem : registryItems) {
            if (registryItem.app.toLowerCase().startsWith("eureka")) {
                eurekaItems.add(registryItem);
            }
        }
        return eurekaItems;
    }
}
