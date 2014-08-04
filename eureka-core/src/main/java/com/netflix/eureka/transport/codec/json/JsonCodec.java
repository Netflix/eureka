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

import java.nio.charset.Charset;
import java.util.List;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * Codec useful for development purposes. It could be also used by WEB browser
 * based clients communicating over WebSocket.
 *
 * @author Tomasz Bak
 */
public class JsonCodec extends ByteToMessageCodec<Message> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setVisibility(JsonMethod.FIELD, Visibility.ANY);
    }

    static class Envelope {
        final String messageType;
        final String contentType;
        final Object content;
        final String correlationId;
        final long timeout;

        Envelope(String messageType, String contentType, Object content) {
            this.messageType = messageType;
            this.contentType = contentType;
            this.content = content;
            correlationId = null;
            timeout = 0;
        }

        Envelope(String messageType, String contentType, Object content, String correlationId, long timeout) {
            this.messageType = messageType;
            this.contentType = contentType;
            this.content = content;
            this.correlationId = correlationId;
            this.timeout = timeout;
        }

        Envelope(String messageType, String correlationId) {
            this.messageType = messageType;
            contentType = null;
            content = null;
            this.correlationId = correlationId;
            timeout = 0;
        }
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof Message;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) {
        Envelope envelope;
        if (msg instanceof UserContentWithAck) {
            UserContentWithAck userContent = (UserContentWithAck) msg;
            envelope = new Envelope(UserContentWithAck.class.getName(), userContent.getContent().getClass().getName(), userContent.getContent(), userContent.getCorrelationId(), userContent.getTimeout());
        } else if (msg instanceof UserContent) {
            UserContent userContent = (UserContent) msg;
            envelope = new Envelope(UserContent.class.getName(), userContent.getContent().getClass().getName(), userContent.getContent());
        } else if (msg instanceof Acknowledgement) {
            Acknowledgement ack = (Acknowledgement) msg;
            envelope = new Envelope(Acknowledgement.class.getName(), ack.getCorrelationId());
        } else {
            throw new IllegalArgumentException("unexpected message of type " + msg.getClass());
        }
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(envelope);
            out.writeBytes(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] array = new byte[in.readableBytes()];
        in.readBytes(array);

        String json = new String(array, Charset.defaultCharset());
        JsonNode jsonNode = MAPPER.readTree(json);

        String messageType = jsonNode.get("messageType").asText();
        Message output;
        if (messageType.equals(Acknowledgement.class.getName())) {
            output = new Acknowledgement(jsonNode.get("correlationId").asText());
        } else {
            String contentType = jsonNode.get("contentType").asText();
            Class<?> contentClass = Class.forName(contentType);
            Object content = MAPPER.readValue(jsonNode.get("content"), contentClass);

            if (messageType.equals(UserContent.class.getName())) {
                output = new UserContent(content);
            } else {
                String cid = jsonNode.get("correlationId").asText();
                long timeout = jsonNode.get("timeout").asLong();
                output = new UserContentWithAck(content, cid, timeout);
            }
        }
        if (output == null) {
            throw new IllegalArgumentException("unexpected message of type " + messageType);
        }
        out.add(output);
    }
}
