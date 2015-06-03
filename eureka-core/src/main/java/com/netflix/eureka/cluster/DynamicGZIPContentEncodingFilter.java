package com.netflix.eureka.cluster;

import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
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
 * This is a modified version of the standard jersey {@link com.sun.jersey.api.client.filter.GZIPContentEncodingFilter},
 * that supports dynamic configuration of request entity compression.
 */
public class DynamicGZIPContentEncodingFilter extends ClientFilter {

    private final EurekaServerConfig config;

    public DynamicGZIPContentEncodingFilter(EurekaServerConfig config) {
        this.config = config;
    }

    @Override
    public ClientResponse handle(ClientRequest request) throws ClientHandlerException {
        if (!request.getHeaders().containsKey(HttpHeaders.ACCEPT_ENCODING)) {
            request.getHeaders().add(HttpHeaders.ACCEPT_ENCODING, "gzip");
        }

        if (request.getEntity() != null) {
            Object o = request.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);
            if (o != null && o.equals("gzip")) {
                request.setAdapter(new Adapter(request.getAdapter()));
            } else if (isCompressionEnabled()) {
                request.getHeaders().add(HttpHeaders.CONTENT_ENCODING, "gzip");
                request.setAdapter(new Adapter(request.getAdapter()));
            }
        }

        ClientResponse response = getNext().handle(request);

        if (response.hasEntity() &&
                response.getHeaders().containsKey(HttpHeaders.CONTENT_ENCODING)) {
            String encodings = response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING);

            if (encodings.equals("gzip")) {
                response.getHeaders().remove(HttpHeaders.CONTENT_ENCODING);
                try {
                    response.setEntityInputStream(new GZIPInputStream(response.getEntityInputStream()));
                } catch (IOException ex) {
                    throw new ClientHandlerException(ex);
                }
            }
        }

        return response;
    }

    private boolean isCompressionEnabled() {
        return config.shouldEnableReplicatedRequestCompression();
    }

    private static final class Adapter extends AbstractClientRequestAdapter {
        Adapter(ClientRequestAdapter cra) {
            super(cra);
        }

        public OutputStream adapt(ClientRequest request, OutputStream out) throws IOException {
            return new GZIPOutputStream(getAdapter().adapt(request, out));
        }
    }
}