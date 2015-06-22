package com.netflix.discovery.util;

/**
 * @author Tomasz Bak
 */
public class EurekaEnvironmentImpl implements EurekaEnvironment {

    public static final String ENV_EC2_VPC_ID = "EC2_VPC_ID";

    @Override
    public boolean isEc2Vpc() {
        return System.getenv(ENV_EC2_VPC_ID) != null;
    }
}
