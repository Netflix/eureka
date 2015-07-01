package com.netflix.eureka2.ext.aws;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author Tomasz Bak
 */
public interface AwsConfiguration {

    @DefaultValue("us-east-1")
    String getRegion();

    String getAwsAccessId();

    String getAwsSecretKey();

    @DefaultValue("30")
    long getRefreshIntervalSec();

    //
    // for the s3 override service
    //

    @DefaultValue("doesNotExit")
    String getBucketName();

    @DefaultValue("eureka2.overrides")
    String getPrefix();
}
