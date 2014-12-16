package com.netflix.eureka2.testkit.junit;

import com.netflix.eureka2.testkit.embedded.EurekaDeployment;
import com.netflix.eureka2.testkit.embedded.EurekaDeployment.EurekaDeploymentBuilder;
import org.junit.rules.ExternalResource;

/**
 * @author Tomasz Bak
 */
public class EmbeddedEurekaDeploymentResource extends ExternalResource {

    private final int writeClusterSize;
    private final int readClusterSize;

    private EurekaDeployment eurekaDeployment;

    public EmbeddedEurekaDeploymentResource(int writeClusterSize, int readClusterSize) {
        this.writeClusterSize = writeClusterSize;
        this.readClusterSize = readClusterSize;
    }

    @Override
    protected void before() throws Throwable {
        eurekaDeployment = new EurekaDeploymentBuilder()
                .withWriteClusterSize(writeClusterSize)
                .withReadClusterSize(readClusterSize)
                .build();
    }

    @Override
    protected void after() {
        if (eurekaDeployment == null) {
            eurekaDeployment.shutdown();
        }
    }
}
