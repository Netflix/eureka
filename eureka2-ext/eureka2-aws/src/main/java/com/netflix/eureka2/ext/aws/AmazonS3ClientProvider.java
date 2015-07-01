package com.netflix.eureka2.ext.aws;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3Client;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

/**
* @author David Liu
*/
public class AmazonS3ClientProvider implements Provider<AmazonS3Client> {

    private final AmazonS3Client amazonS3Client;

    @Inject
    public AmazonS3ClientProvider(S3OverridesConfiguration configuration) {
        if (configuration.getAwsAccessId() != null && configuration.getAwsSecretKey() != null) {
            amazonS3Client = new AmazonS3Client(new BasicAWSCredentials(configuration.getAwsAccessId(), configuration.getAwsSecretKey()));
        } else {
            amazonS3Client = new AmazonS3Client(new InstanceProfileCredentialsProvider());
        }

        String region = configuration.getRegion().trim().toLowerCase();
        amazonS3Client.setEndpoint("ec2." + region + ".amazonaws.com");
    }

    @Override
    public AmazonS3Client get() {
        return amazonS3Client;
    }

    @PreDestroy
    public void shutdown() {
        amazonS3Client.shutdown();
    }
}