package com.netflix.eureka2.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Codec API. By providing this common abstraction, the same codecs can be used by
 * transport, and non-transport related modules (for example registry external backup).
 *
 * @author Tomasz Bak
 */
public interface EurekaCodec<T> {

    boolean accept(Class<?> valueType);

    void encode(T value, OutputStream output) throws IOException;

    T decode(InputStream source) throws IOException;
}
