package com.netflix.eureka2;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;

import java.util.List;

public class RegistryStreamUtil {

    public static boolean sendRegistryOverWebsocket(final ObservableConnection<WebSocketFrame, WebSocketFrame> webSocketConn,
                                                    List<RegistryStream.RegistryItem> registryItems,
                                                    Gson gson) {
        if (webSocketConn.getChannel().isOpen() && registryItems.size() > 0) {
            final String jsonStr = gson.toJson(registryItems);
            final ByteBuf respByteBuf = webSocketConn.getAllocator().buffer().writeBytes(jsonStr.getBytes());
            webSocketConn.writeAndFlush(new TextWebSocketFrame(respByteBuf));
            return true;
        } else {
            return false;
        }
    }

}
