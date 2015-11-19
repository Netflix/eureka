package com.netflix.eureka.cluster;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.netflix.eureka.EurekaServerConfig;
import com.sun.jersey.api.client.AbstractClientRequestAdapter;
import com.sun.jersey.api.client.ClientHandlerException;
import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientRequestAdapter;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.filter.ClientFilter;

/**
 * Eureka specific GZIP content filter handler.
 */
public class DynamicGZIPContentEncodingFilter extends ClientFilter {

    private static final String GZIP_ENCODING = "gzip";

    private final EurekaServerConfig config;

    public DynamicGZIPContentEncodingFilter(EurekaServerConfig config) {
        this.config = config;
    }

    @Override
    public ClientResponse handle(ClientRequest request) {
        // If 'Accept-Encoding' is not set, assume gzip as a default
        if (!request.getHeaders().containsKey(HttpHeaders.ACCEPT_ENCODING)) {
            request.getHeaders().add(HttpHeaders.ACCEPT_ENCODING, GZIP_ENCODING);
        }

        if (request.getEntity() != null) {
            Object requestEncoding = request.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
            if (GZIP_ENCODING.equals(requestEncoding)) {
                request.setAdapter(new GzipAdapter(request.getAdapter()));
            } else if (isCompressionEnabled()) {
                request.getHeaders().add(HttpHeaders.CONTENT_ENCODING, GZIP_ENCODING);
                request.setAdapter(new GzipAdapter(request.getAdapter()));
            }
        }

        ClientResponse response = getNext().handle(request);

        String responseEncoding = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
        if (response.hasEntity() && GZIP_ENCODING.equals(responseEncoding)) {
            response.getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
            decompressResponse(response);
        }
        return response;
    }

    private boolean isCompressionEnabled() {
        return config.shouldEnableReplicatedRequestCompression();
    }

    private static void decompressResponse(ClientResponse response) {
        InputStream entityInputStream = response.getEntityInputStream();
        GZIPInputStream uncompressedIS;
        try {
            uncompressedIS = new GZIPInputStream(entityInputStream);
        } catch (IOException ex) {
            try {
                entityInputStream.close();
            } catch (IOException ignored) {
            }
            throw new ClientHandlerException(ex);
        }
        response.setEntityInputStream(uncompressedIS);
    }

    private static final class GzipAdapter extends AbstractClientRequestAdapter {
        GzipAdapter(ClientRequestAdapter cra) {
            super(cra);
        }

        @Override
        public OutputStream adapt(ClientRequest request, OutputStream out) throws IOException {
            return new GZIPOutputStream(getAdapter().adapt(request, out));
        }
    }
}