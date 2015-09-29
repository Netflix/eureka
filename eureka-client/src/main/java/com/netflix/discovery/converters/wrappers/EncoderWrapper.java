package com.netflix.discovery.converters.wrappers;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author David Liu
 */
public interface EncoderWrapper extends CodecWrapperBase {
    <T> String encode(T object) throws IOException;

    <T> void encode(T object, OutputStream outputStream) throws IOException;
}
