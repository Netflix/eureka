/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.utils;

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
