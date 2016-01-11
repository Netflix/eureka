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

package com.netflix.eureka2.transport;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.StdSource;
import com.netflix.eureka2.model.channel.StdReplicationServerHello;
import com.netflix.eureka2.model.transport.StdProtocolMessageEnvelope;
import com.netflix.eureka2.spi.codec.EurekaCodec;
import com.netflix.eureka2.spi.codec.EurekaCodecFactory;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.model.transport.ProtocolMessageEnvelope;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;

/**
 */
public class JacksonNettyMessageCodec extends ByteToMessageCodec<Object> {

    private final EurekaCodecFactory codecFactory = EurekaCodecFactory.getDefaultFactory();
    private final Class<? extends ProtocolMessageEnvelope> envelopeType;

    public JacksonNettyMessageCodec() {
        // We need active envelope type, so we use current factory to create an instance with this type
        this.envelopeType = TransportModel.getDefaultModel().newEnvelope(
                ProtocolMessageEnvelope.ProtocolType.Registration,
                TransportModel.getDefaultModel().newAcknowledgement())
                .getClass();
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        if (msg instanceof ProtocolMessageEnvelope) {
            Object internal = ((ProtocolMessageEnvelope) msg).getMessage();
            if (codecFactory.accept(internal.getClass())) {
                return true;
            }
            throw new IllegalArgumentException("Cannot serialize message of type " + internal.getClass().getName());
        }
        // Netty will silently pass this forward, while this is a protocol violation, and channel should
        // be shut down immediately.
        throw new IllegalArgumentException("Cannot serialize message of type " + msg.getClass().getName());
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws Exception {
        ByteBufOutputStream byteBufOutputStream = new ByteBufOutputStream(out);
        EurekaCodec eurekaCodec = codecFactory.getCodec();
        eurekaCodec.encode(msg, byteBufOutputStream);
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() == 0) {
            return;
        }
        ByteBufInputStream byteBufInputStream = new ByteBufInputStream(in);
        EurekaCodec eurekaCodec = codecFactory.getCodec();
        ProtocolMessageEnvelope decodedValue = eurekaCodec.decode(byteBufInputStream, envelopeType);
        out.add(decodedValue);
    }

    public static void main(String[] args) throws IOException {
        EurekaCodec codec = EurekaCodecFactory.getDefaultFactory().getCodec();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        StdReplicationServerHello hello = new StdReplicationServerHello(new StdSource(Source.Origin.INTERESTED, "test", 123));
        StdProtocolMessageEnvelope envelope = new StdProtocolMessageEnvelope(StdProtocolMessageEnvelope.ProtocolType.Registration, hello);

        codec.encode(envelope, output);

        System.out.println(new String(output.toByteArray()));

        StdProtocolMessageEnvelope decoded = codec.decode(new ByteArrayInputStream(output.toByteArray()), StdProtocolMessageEnvelope.class);
        System.out.println(decoded.getMessage());
    }
}
