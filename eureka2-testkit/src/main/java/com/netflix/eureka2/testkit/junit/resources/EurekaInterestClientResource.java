package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

/**
 * @author Tomasz Bak
 */
public class EurekaInterestClientResource extends EurekaExternalResource {

    private final ServerResolver serverResolver;

    private EurekaInterestClient interestClient;

    public EurekaInterestClientResource(ServerResolver serverResolver) {
        this.serverResolver = serverResolver;
    }

    @Override
    protected void before() throws Throwable {
        interestClient = Eureka.newInterestClientBuilder().withServerResolver(serverResolver).build();
    }

    @Override
    protected void after() {
        if (interestClient != null) {
            interestClient.shutdown();
            interestClient = null;
        }
    }

    public EurekaInterestClient getEurekaClient() {
        return interestClient;
    }
}
