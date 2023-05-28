package com.netflix.discovery.shared.transport.jersey2;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MultivaluedMap;
import java.util.List;
import java.util.Map;

/**
 * A version of Jersey2 {@link com.netflix.discovery.shared.transport.EurekaHttpClient} to be used by applications.
 *
 * @author David Liu
 */
public class Jersey2ApplicationClient extends AbstractJersey2EurekaHttpClient {

    private final MultivaluedMap<String, Object> additionalHeaders;

    public Jersey2ApplicationClient(Client jerseyClient, String serviceUrl, MultivaluedMap<String, Object> additionalHeaders) {
        super(jerseyClient, serviceUrl);
        this.additionalHeaders = additionalHeaders;
    }

    @Override
    protected void addExtraHeaders(Builder webResource) {
        if (additionalHeaders != null) {
            for (Map.Entry<String, List<Object>> entry : additionalHeaders.entrySet()) {
                webResource.header(entry.getKey(), entry.getValue());
            }
        }
    }
}
