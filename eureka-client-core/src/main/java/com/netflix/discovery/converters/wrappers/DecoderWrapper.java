package com.netflix.discovery.converters.wrappers;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author David Liu
 */
public interface DecoderWrapper extends CodecWrapperBase {

    <T> T decode(String textValue, Class<T> type) throws IOException;

    <T> T decode(InputStream inputStream, Class<T> type) throws IOException;
}
