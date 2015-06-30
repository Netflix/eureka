package com.netflix.eureka2.ext.aws;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author David Liu
 */
public interface S3OverridesConfiguration extends AwsConfiguration {

    String getBucketName();

    @DefaultValue("eureka2.overrides")
    String getPrefix();
}
