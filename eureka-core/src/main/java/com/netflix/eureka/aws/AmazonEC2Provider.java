package com.netflix.eureka.aws;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Provider;

import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;

/**
 * @author Tomasz Bak
 */
public class AmazonEC2Provider implements Provider<AmazonEC2> {

    private final AmazonEC2Client amazonEC2;

    @Inject
    public AmazonEC2Provider(EurekaClientConfig eurekaClientConfig, EurekaServerConfig eurekaServerConfig) {
        String aWSAccessId = eurekaServerConfig.getAWSAccessId();
        String aWSSecretKey = eurekaServerConfig.getAWSSecretKey();

        if (null != aWSAccessId && !"".equals(aWSAccessId) && null != aWSSecretKey && !"".equals(aWSSecretKey)) {
            amazonEC2 = new AmazonEC2Client(new BasicAWSCredentials(aWSAccessId, aWSSecretKey));
        } else {
            amazonEC2 = new AmazonEC2Client(new InstanceProfileCredentialsProvider());
        }

        String region = eurekaClientConfig.getRegion().trim().toLowerCase();
        amazonEC2.setEndpoint("ec2." + region + ".amazonaws.com");
    }

    @Override
    public AmazonEC2 get() {
        return amazonEC2;
    }

    @PreDestroy
    public void shutdown() {
        if(amazonEC2 != null) {
            amazonEC2.shutdown();
        }
    }
}
