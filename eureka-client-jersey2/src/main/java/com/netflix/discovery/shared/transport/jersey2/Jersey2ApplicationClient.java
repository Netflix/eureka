package com.netflix.discovery.shared.transport.jersey2;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import java.util.Map;

/**
 * A version of Jersey2 {@link com.netflix.discovery.shared.transport.EurekaHttpClient} to be used by applications.
 *
 * @author David Liu
 */
public class Jersey2ApplicationClient extends AbstractJersey2EurekaHttpClient {

    private final Map<String, String> additionalHeaders;

    public Jersey2ApplicationClient(Client jerseyClient, String serviceUrl, Map<String, String> additionalHeaders) {
        super(jerseyClient, serviceUrl);
        this.additionalHeaders = additionalHeaders;
    }

    @Override
    protected void addExtraHeaders(Builder webResource) {
        if (additionalHeaders != null) {
            for (String key : additionalHeaders.keySet()) {
                webResource.header(key, additionalHeaders.get(key));
            }
        }
    }
}
