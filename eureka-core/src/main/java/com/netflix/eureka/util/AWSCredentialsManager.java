/*
 * Copyright 2012 Dowd and Associates
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerConfigurationManager;

/**
 * A utility to get the AWS credentials to use.
 *
 * This utility will try to use the provided credentials, but if no
 * credentials are provided it will try to fall back to an IAM role.
 *
 * @author Eric Dowd
 */
public class AWSCredentialsManager {
    private static final Logger logger = LoggerFactory
    .getLogger(AWSCredentialsManager.class);
    private EurekaServerConfig eurekaConfig = EurekaServerConfigurationManager
    .getInstance().getConfiguration();
    private final InstanceProfileCredentialsProvider iamCredProvider;

    private static final AWSCredentialsManager s_instance =
            new AWSCredentialsManager();

    public static AWSCredentialsManager getInstance() {
        return s_instance;
    }

    private AWSCredentialsManager() {
        this.iamCredProvider = new InstanceProfileCredentialsProvider();
    }

    /**
     * Gets the AWS credentials to use.
     *
     * @return the AWS credentials
     */
    public AWSCredentials getCredentials() {
        String aWSAccessId = eurekaConfig.getAWSAccessId();
        String aWSSecretKey = eurekaConfig.getAWSSecretKey();

        if (null != aWSAccessId && !"".equals(aWSAccessId.trim()) &&
                null != aWSSecretKey && !"".equals(aWSSecretKey.trim()))
        {
            return new BasicAWSCredentials(aWSAccessId, aWSSecretKey);
        }
        else
        {
            return iamCredProvider.getCredentials();
        }
    }
}
