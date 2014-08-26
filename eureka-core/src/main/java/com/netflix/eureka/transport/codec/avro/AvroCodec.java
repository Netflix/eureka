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

package com.netflix.eureka.transport.codec.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.utils.TransportModel;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;

/**
 * TODO Possibly we can do some optimizations here. For now lets keep it simple.
 *
 * @author Tomasz Bak
 */
class AvroCodec extends ByteToMessageCodec<Object> {

    private final TransportModel model;
    private final AvroSchemaArtifacts avroSchemaArtifacts;

    public AvroCodec(TransportModel model) {
        this.model = model;
        avroSchemaArtifacts = new AvroSchemaArtifacts(model);
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof Acknowledgement || model.isProtocolMessage(msg);
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        ReflectDatumWriter<Object> writer = new ReflectDatumWriter<Object>(avroSchemaArtifacts.getRootSchema(), avroSchemaArtifacts.getReflectData());
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Encoder encoder = EncoderFactory.get().binaryEncoder(bos, null);

        try {
            writer.write(msg, encoder);
            encoder.flush();
            bos.close();
        } catch (IOException e) {
            throw new EncoderException("Avro encoding failure of object of type " + msg.getClass().getName(), e);
        }

        byte[] bytes = bos.toByteArray();
        out.ensureWritable(bytes.length);
        out.writeBytes(bytes);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        byte[] array = new byte[in.readableBytes()];
        in.readBytes(array);

        ReflectDatumReader<Object> reader = new ReflectDatumReader<Object>(
                avroSchemaArtifacts.getRootSchema(),
                avroSchemaArtifacts.getRootSchema(),
                avroSchemaArtifacts.getReflectData());
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(array, null);

        try {
            out.add(reader.read(null, decoder));
        } catch (IOException e) {
            throw new DecoderException("Avro decoding failure", e);
        }
    }
}
