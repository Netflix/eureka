package com.netflix.discovery.shared.resolver.aws;

import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;

/**
 * Used to expose test onstructor to other packages
 *
 * @author David Liu
 */
public class TestEurekaHttpResolver extends EurekaHttpResolver {
    public TestEurekaHttpResolver(EurekaClientConfig clientConfig, EurekaHttpClientFactory clientFactory, String vipAddress) {
        super(clientConfig, clientFactory, vipAddress);
    }
}
