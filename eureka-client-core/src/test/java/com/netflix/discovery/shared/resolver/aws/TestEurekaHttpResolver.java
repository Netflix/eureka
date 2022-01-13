package com.netflix.discovery.shared.resolver.aws;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;

/**
 * Used to expose test constructor to other packages
 *
 * @author David Liu
 */
public class TestEurekaHttpResolver extends EurekaHttpResolver {
    public TestEurekaHttpResolver(EurekaClientConfig clientConfig, EurekaTransportConfig transportConfig, EurekaHttpClientFactory clientFactory, String vipAddress) {
        super(clientConfig, transportConfig, clientFactory, vipAddress);
    }
}
