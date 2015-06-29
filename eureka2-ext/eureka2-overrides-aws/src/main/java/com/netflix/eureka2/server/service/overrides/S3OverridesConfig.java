package com.netflix.eureka2.server.service.overrides;

/**
 * Config class for S3 override registry
 *
 * @author David Liu
 */
public interface S3OverridesConfig {

    String getBucketName();

    String getPrefix();
}
