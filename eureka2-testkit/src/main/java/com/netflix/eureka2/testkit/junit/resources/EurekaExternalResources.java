package com.netflix.eureka2.testkit.junit.resources;

import java.util.ArrayList;
import java.util.List;

import org.junit.rules.ExternalResource;

/**
 * Sometimes we need to postpone a resource creation, until some initial
 * part of a test is executed. As before/after methods of {@link ExternalResource}
 * are protected, it is not possible to call them directly. {@link EurekaExternalResource}
 * solves this problem by providing {@link EurekaExternalResource#connect()} method.
 * The resource shutdown process can be handled directly, or could be delegated to
 * {@link EurekaExternalResources} which work as a cleanup registry, which itself
 * is JUnit {@link ExternalResource}.
 *
 * @author Tomasz Bak
 */
public class EurekaExternalResources extends ExternalResource {

    public abstract static class EurekaExternalResource extends ExternalResource {

        public void connect() {
            try {
                before();
            } catch (Throwable cause) {
                throw new RuntimeException(cause);
            }
        }

        public void close() {
            after();
        }
    }

    private final List<EurekaExternalResource> eurekaExternalResources = new ArrayList<>();

    public <T extends EurekaExternalResource> T connect(T resource) {
        resource.connect();
        eurekaExternalResources.add(resource);
        return resource;
    }

    @Override
    protected void after() {
        for (EurekaExternalResource r : eurekaExternalResources) {
            r.close();
        }
    }
}
