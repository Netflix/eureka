package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.EurekaClientBuilder;
import com.netflix.eureka2.client.interest.EurekaInterestClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

/**
 * @author Tomasz Bak
 */
public class EurekaInterestClientResource extends EurekaExternalResource {

    private final ServerResolver serverResolver;

    private EurekaClient eurekaClient;

    public EurekaInterestClientResource(ServerResolver serverResolver) {
        this.serverResolver = serverResolver;
    }

    @Override
    protected void before() throws Throwable {
        eurekaClient = EurekaClientBuilder.discoveryBuilder().withReadServerResolver(serverResolver).build();
    }

    @Override
    protected void after() {
        if (eurekaClient != null) {
            eurekaClient.shutdown();
            eurekaClient = null;
        }
    }

    public EurekaInterestClient getEurekaClient() {
        return eurekaClient;
    }
}
