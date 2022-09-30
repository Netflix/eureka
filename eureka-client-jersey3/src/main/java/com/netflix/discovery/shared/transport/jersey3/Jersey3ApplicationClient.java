package com.netflix.discovery.shared.transport.jersey3;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.Map;

/**
 * A version of Jersey3 {@link com.netflix.discovery.shared.transport.EurekaHttpClient} to be used by applications.
 *
 * @author David Liu
 */
public class Jersey3ApplicationClient extends AbstractJersey3EurekaHttpClient {

    private final MultivaluedMap<String, Object> additionalHeaders;

    public Jersey3ApplicationClient(Client jerseyClient, String serviceUrl, MultivaluedMap<String, Object> additionalHeaders) {
        super(jerseyClient, serviceUrl);
        this.additionalHeaders = additionalHeaders;
    }

    @Override
    protected void addExtraHeaders(Builder webResource) {
        if (additionalHeaders != null) {
            for (Map.Entry<String, List<Object>> entry: additionalHeaders.entrySet()) {
                webResource.header(entry.getKey(), entry.getValue());
            }
        }
    }
}
