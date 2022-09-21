package com.netflix.discovery;

import jakarta.ws.rs.client.ClientRequestFilter;

/**
 * Jersey3 implementation of DiscoveryClientOptionalArgs that supports supplying {@link ClientRequestFilter}
 */
public class Jersey3DiscoveryClientOptionalArgs extends AbstractDiscoveryClientOptionalArgs<ClientRequestFilter> {

}
