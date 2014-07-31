package com.netflix.eureka.transport.codec.avro;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.netflix.eureka.transport.Message;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.apache.avro.Schema;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.Encoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.reflect.ReflectDatumReader;
import org.apache.avro.reflect.ReflectDatumWriter;

/**
 * TODO Possibly we can do some optimizations here. For now lets keep it simple.
 * TODO Error handling in case message cannot be encoded/decoded.
 *
 * @author Tomasz Bak
 */
class AvroCodec extends ByteToMessageCodec<Message> {

    private Schema schema;

    public AvroCodec(Schema schema) {
        this.schema = schema;
    }

    @Override
    public boolean acceptOutboundMessage(Object msg) throws Exception {
        return msg instanceof Message;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, Message msg, ByteBuf out) throws Exception {
        try {
            ReflectDatumWriter writer = new ReflectDatumWriter<Message>(schema);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            Encoder encoder = EncoderFactory.get().binaryEncoder(bos, null);

            writer.write(msg, encoder);
            encoder.flush();
            bos.close();

            byte[] bytes = bos.toByteArray();
            out.ensureWritable(bytes.length);
            out.writeBytes(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        byte[] array = new byte[in.readableBytes()];
        in.readBytes(array);

        ReflectDatumReader<Message> reader = new ReflectDatumReader<Message>(schema);
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(array, null);
        Message msg = reader.read(null, decoder);

        out.add(msg);
    }
}
