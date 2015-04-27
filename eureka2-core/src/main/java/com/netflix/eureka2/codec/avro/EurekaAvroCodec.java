package com.netflix.eureka2.codec.avro;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;

import com.netflix.eureka2.codec.EurekaCodec;
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
public class EurekaAvroCodec<T> implements EurekaCodec<T> {

    private final Set<Class<?>> protocolTypes;

    private final ReflectDatumWriter<Object> datumWriter;
    private final ReflectDatumReader<Object> datumReader;
    private BinaryEncoder encoder;
    private BinaryDecoder decoder;

    public EurekaAvroCodec(Set<Class<?>> protocolTypes, Schema rootSchema) {
        this(protocolTypes, rootSchema, new SchemaReflectData(rootSchema));
    }

    public EurekaAvroCodec(Set<Class<?>> protocolTypes, Schema rootSchema, SchemaReflectData reflectData) {
        this.protocolTypes = protocolTypes;
        this.datumWriter = new ReflectDatumWriter<>(rootSchema, reflectData);
        this.datumReader = new ReflectDatumReader<>(rootSchema, rootSchema, reflectData);
    }

    @Override
    public boolean accept(Class<?> valueType) {
        return protocolTypes.contains(valueType);
    }

    @Override
    public void encode(T value, OutputStream output) throws IOException {
        encoder = EncoderFactory.get().binaryEncoder(output, encoder);
        datumWriter.write(value, encoder);
        encoder.flush();
        output.close();
    }

    @Override
    public T decode(InputStream source) throws IOException {
        decoder = DecoderFactory.get().binaryDecoder(source, decoder);
        return (T) datumReader.read(null, decoder);
    }
}
