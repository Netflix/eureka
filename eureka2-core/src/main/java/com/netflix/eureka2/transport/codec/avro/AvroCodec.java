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

package com.netflix.eureka2.transport.codec.avro;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import com.netflix.eureka2.transport.Acknowledgement;
import com.netflix.eureka2.transport.codec.AbstractEurekaCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;

/**
 * @author Tomasz Bak
 */
public class AvroCodec extends AbstractEurekaCodec {

    private final Set<Class<?>> protocolTypes;

    private final ReflectDatumWriter<Object> datumWriter;
    private final ReflectDatumReader<Object> datumReader;
    private BinaryEncoder encoder;
    private BinaryDecoder decoder;

    /**
     * This constructor creates a new {@link SchemaReflectData}, which is prohibitively
     * expensive. It is however convenient for testing.
     */
    public AvroCodec(Set<Class<?>> protocolTypes, Schema rootSchema) {
        this(protocolTypes, rootSchema, new SchemaReflectData(rootSchema));
    }

    public AvroCodec(Set<Class<?>> protocolTypes, Schema rootSchema, SchemaReflectData reflectData) {
        this.protocolTypes = protocolTypes;
        this.datumWriter = new ReflectDatumWriter<>(rootSchema, reflectData);
        this.datumReader = new ReflectDatumReader<>(rootSchema, rootSchema, reflectData);
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof Acknowledgement || protocolTypes.contains(msg.getClass());
    }

    @Override
    public void encode(ChannelHandlerContext ctx, Object msg, ByteBuf out) {
        ByteBufOutputStream os = new ByteBufOutputStream(out);
        encoder = EncoderFactory.get().binaryEncoder(os, encoder);

        try {
            datumWriter.write(msg, encoder);
            encoder.flush();
            os.close();
        } catch (IOException e) {
            throw new EncoderException("Avro encoding failure of object of type " + msg.getClass().getName(), e);
        }
    }

    @Override
    public void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        ByteBufInputStream is = new ByteBufInputStream(in);
        decoder = DecoderFactory.get().binaryDecoder(is, decoder);

        try {
            out.add(datumReader.read(null, decoder));
        } catch (IOException e) {
            throw new DecoderException("Avro decoding failure", e);
        }
    }
}
