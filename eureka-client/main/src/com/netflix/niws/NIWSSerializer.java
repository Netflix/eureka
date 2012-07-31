package com.netflix.niws;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class NIWSSerializer implements Serializer, IPayloadObjectConverter {

    private IPayloadObjectConverter converter;
    
    public NIWSSerializer(IPayloadObjectConverter converter) {
        this.converter = converter;
    }
    
    @Override
    public byte[] serialize(IPayload object, String contentType) throws IOException {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        byte[] result = null;
        converter.write(object, bao, contentType);
        bao.close();            
        result = bao.toByteArray();
        return result;
    }

    @Override
    public IPayload read(InputStream is, Class<IPayload> type,
            String contentType) throws IOException {
        return converter.read(is, type, contentType);
    }

    @Override
    public void write(Object object, OutputStream os, String contentType)
            throws IOException {
        converter.write(object, os, contentType);        
    }
}
