package com.netflix.eureka2.ext.aws;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;

/**
 * @author Tomasz Bak
 */
public class AmazonAutoScalingProvider implements Provider<AmazonAutoScaling> {

    private final AmazonAutoScaling amazonAutoScaling;

    @Inject
    public AmazonAutoScalingProvider(AwsConfiguration configuration) {
        if (configuration.getAwsAccessId() != null && configuration.getAwsSecretKey() != null) {
            amazonAutoScaling = new AmazonAutoScalingClient(new BasicAWSCredentials(configuration.getAwsAccessId(), configuration.getAwsSecretKey()));
        } else {
            amazonAutoScaling = new AmazonAutoScalingClient(new InstanceProfileCredentialsProvider());
        }

        String region = configuration.getRegion().trim().toLowerCase();
        amazonAutoScaling.setEndpoint("ec2." + region + ".amazonaws.com");
    }

    @Override
    public AmazonAutoScaling get() {
        return amazonAutoScaling;
    }

    @PreDestroy
    public void shutdown() {
        if (amazonAutoScaling != null) {
            amazonAutoScaling.shutdown();
        }
    }
}
