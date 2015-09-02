package com.netflix.eureka2.codec;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import rx.Observable;

/**
 * Methods that help bridging {@link EurekaCodec} API with RxNetty input/output mechanics.
 *
 * @author Tomasz Bak
 */
public final class RxNettyCodecUtils {

    private RxNettyCodecUtils() {
    }

    public static <T> Observable<Void> writeValue(EurekaCodec<T> codec, T value, HttpServerResponse<ByteBuf> response) {
        ByteBuf byteBuf = Unpooled.buffer();
        ByteBufOutputStream os = new ByteBufOutputStream(byteBuf);
        try {
            codec.encode(value, os);
        } catch (IOException e) {
            return Observable.error(e);
        }
        response.writeBytes(byteBuf);
        return Observable.empty();
    }
}
