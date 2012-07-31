package com.netflix.niws;

import java.io.IOException;

public interface Serializer {

    public byte[] serialize(IPayload object, String contentType) throws IOException;
}
