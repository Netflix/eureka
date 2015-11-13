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

package com.netflix.eureka2.transport.codec;

import java.util.List;

import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

/**
 * @author Tomasz Bak
 */
public class EurekaCodecWrapper extends ByteToMessageCodec<Object> {

    private final EurekaCodecFactory codecFactory;

    public EurekaCodecWrapper(EurekaCodecFactory codecFactory) {
        this.codecFactory = codecFactory;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return codecFactory.accept(msg.getClass());
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(out);
        EurekaCodec eurekaCodec = codecFactory.getCodec();
        eurekaCodec.encode(new ProtocolMessageEnvelope(msg), byteBufOutputStream);
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        ByteBufInputStream byteBufInputStream = new ByteBufInputStream(in);
        EurekaCodec eurekaCodec = codecFactory.getCodec();
        ProtocolMessageEnvelope decodedValue = eurekaCodec.decode(byteBufInputStream, ProtocolMessageEnvelope.class);
        out.add(decodedValue.getMessage());
    }
}
