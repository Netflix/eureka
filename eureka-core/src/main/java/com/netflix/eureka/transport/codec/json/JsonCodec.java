/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka.transport.codec.json;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.utils.TransportModel;
import com.netflix.eureka.utils.Json;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.codehaus.jackson.JsonNode;

/**
 * Codec useful for development purposes. It could be also used by WEB browser
 * based clients communicating over WebSocket.
 *
 * @author Tomasz Bak
 */
public class JsonCodec extends ByteToMessageCodec<Object> {

    private final TransportModel model;

    static class Envelope {
        final String type;
        final Object content;

        Envelope(String type, Object content) {
            this.type = type;
            this.content = content;
        }
    }

    public JsonCodec(TransportModel model) {
        this.model = model;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof Acknowledgement || model.isProtocolMessage(msg);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) throws IOException {
        Envelope envelope = new Envelope(msg.getClass().getName(), msg);
        byte[] bytes = Json.getMapper().writeValueAsBytes(envelope);
        out.writeBytes(bytes);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] array = new byte[in.readableBytes()];
        in.readBytes(array);

        String json = new String(array, Charset.defaultCharset());
        JsonNode jsonNode = Json.getMapper().readTree(json);

        String messageType = jsonNode.get("type").asText();
        Class<?> contentClass = Class.forName(messageType);
        Object content = Json.getMapper().readValue(jsonNode.get("content"), contentClass);
        out.add(content);
    }
}
