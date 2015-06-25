package com.netflix.discovery.util;

/**
 * Eureka comes with embedded support for AWS environment. To support VPC, some
 * features (mostly related to Eureka cluster discovery) must be modified, and the logic
 * triggered will depend on type of cloud the service is running in.
 * This interface provides a basic information about the environment in which the discovery
 * client/server is running.
 *
 * @author Tomasz Bak
 */
public interface EurekaEnvironment {

    /**
     * Returns true if a server is running on EC2 VPC instance.
     */
    boolean isEc2Vpc();
}
