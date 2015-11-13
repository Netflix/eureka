package com.netflix.eureka2.codec;

import java.io.IOException;

import com.netflix.eureka2.spi.codec.EurekaCodec;
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

    public static <T> Observable<Void> writeValue(EurekaCodec codec, T value, HttpServerResponse<ByteBuf> response) {
        ByteBuf byteBuf = Unpooled.buffer();
        ByteBufOutputStream os = new ByteBufOutputStream(byteBuf);
        try {
            codec.encode(value, os);
        } catch (Exception e) {
            return Observable.error(e);
        }
        response.writeBytes(byteBuf);
        return Observable.empty();
    }
}
