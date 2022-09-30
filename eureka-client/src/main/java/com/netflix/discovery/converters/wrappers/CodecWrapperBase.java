package com.netflix.discovery.converters.wrappers;

import jakarta.ws.rs.core.MediaType;

/**
 * @author David Liu
 */
public interface CodecWrapperBase {

    String codecName();

    boolean support(MediaType mediaType);
}
