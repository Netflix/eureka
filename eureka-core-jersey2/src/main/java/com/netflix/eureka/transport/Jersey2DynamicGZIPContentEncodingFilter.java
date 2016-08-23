package com.netflix.eureka.transport;

import com.netflix.eureka.EurekaServerConfig;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;

public class Jersey2DynamicGZIPContentEncodingFilter implements ClientRequestFilter, ClientResponseFilter {

    private final EurekaServerConfig config;

    public Jersey2DynamicGZIPContentEncodingFilter(EurekaServerConfig config) {
        this.config = config;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (!requestContext.getHeaders().containsKey(HttpHeaders.ACCEPT_ENCODING)) {
            requestContext.getHeaders().add(HttpHeaders.ACCEPT_ENCODING, "gzip");
        }

        if (hasEntity(requestContext) && isCompressionEnabled()) {
            Object contentEncoding = requestContext.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
            if (!"gzip".equals(contentEncoding)) {
                requestContext.getHeaders().add(HttpHeaders.CONTENT_ENCODING, "gzip");
            }
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Object contentEncoding = responseContext.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if ("gzip".equals(contentEncoding)) {
            responseContext.getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
        }
    }

    private boolean hasEntity(ClientRequestContext requestContext) {
        return false;
    }

    private boolean isCompressionEnabled() {
        return config.shouldEnableReplicatedRequestCompression();
    }

}