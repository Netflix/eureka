package com.netflix.eureka2.testkit.junit.resources;

import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClientBuilder;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.testkit.junit.resources.EurekaExternalResources.EurekaExternalResource;

/**
 * @author Tomasz Bak
 */
public class EurekaRegistrationClientResource extends EurekaExternalResource {

    private final ServerResolver serverResolver;

    private EurekaRegistrationClient registrationClient;

    public EurekaRegistrationClientResource(ServerResolver serverResolver) {
        this.serverResolver = serverResolver;
    }

    @Override
    protected void before() throws Throwable {
        registrationClient = new EurekaRegistrationClientBuilder().withServerResolver(serverResolver).build();
    }

    @Override
    protected void after() {
        if (registrationClient != null) {
            registrationClient.shutdown();
            registrationClient = null;
        }
    }

    public EurekaRegistrationClient getEurekaClient() {
        return registrationClient;
    }
}