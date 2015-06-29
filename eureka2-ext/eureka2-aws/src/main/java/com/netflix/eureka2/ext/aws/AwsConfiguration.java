package com.netflix.eureka2.ext.aws;

import com.netflix.archaius.annotations.DefaultValue;

/**
 * @author Tomasz Bak
 */
public interface AwsConfiguration {

    String getRegion();

    String getAwsAccessId();

    String getAwsSecretKey();

    @DefaultValue("30")
    long getRefreshIntervalSec();
}
