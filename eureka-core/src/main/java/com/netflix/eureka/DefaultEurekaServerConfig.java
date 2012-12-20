/*
 * Copyright 2012 Netflix, Inc.
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

package com.netflix.eureka;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

/**
 * 
 * A default implementation of eureka  server configuration as required by
 * {@link EurekaServerConfig}.
 * 
 * <p>
 * The information required for configuring eureka server is provided in a
 * configuration file.The configuration file is searched for in the classpath
 * with the name specified by the property <em>eureka.server.props</em> and with
 * the suffix <em>.properties</em>. If the property is not specified,
 * <em>eureka-server.properties</em> is assumed as the default.The properties
 * that are looked up uses the <em>namespace</em> passed on to this class.
 * </p>
 * 
 * <p>
 * If the <em>eureka.environment</em> property is specified, additionally
 * <em>eureka-server-<eureka.environment>.properties</em> is loaded in addition
 * to <em>eureka-server.properties</em>.
 * </p>
 * 
 * @author Karthik Ranganathan
 * 
 */
public class DefaultEurekaServerConfig implements EurekaServerConfig {
    private static final String ARCHAIUS_DEPLOYMENT_ENVIRONMENT = "archaius.deployment.environment";
    private static final String TEST = "test";
    private static final String EUREKA_ENVIRONMENT = "eureka.environment";
    private static final Logger logger = LoggerFactory
    .getLogger(DefaultEurekaServerConfig.class);
    private static final DynamicPropertyFactory configInstance = com.netflix.config.DynamicPropertyFactory
    .getInstance();
    private static final DynamicStringProperty EUREKA_PROPS_FILE = DynamicPropertyFactory
    .getInstance().getStringProperty("eureka.server.props",
    "eureka-server");
    private String namespace = "eureka.";

    public DefaultEurekaServerConfig() {
        init();
    }

    public DefaultEurekaServerConfig(String namespace) {
        this.namespace = namespace;
        init();
    }

    private void init() {
        String env = ConfigurationManager.getConfigInstance().getString(
                EUREKA_ENVIRONMENT, TEST);
        ConfigurationManager.getConfigInstance().setProperty(
                ARCHAIUS_DEPLOYMENT_ENVIRONMENT, env);

        String eurekaPropsFile = EUREKA_PROPS_FILE.get();
        try {
            // ConfigurationManager
            // .loadPropertiesFromResources(eurekaPropsFile);
            ConfigurationManager
            .loadCascadedPropertiesFromResources(eurekaPropsFile);
        } catch (IOException e) {
            logger.warn(
                    "Cannot find the properties specified : {}. This may be okay if there are other environment specific properties or the configuration is installed with a different mechanism.",
                    eurekaPropsFile);
        }
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.eureka.EurekaServerConfig#getAWSAccessId()
     */
    @Override
    public String getAWSAccessId() {
        String aWSAccessId = configInstance
        .getStringProperty(namespace + "awsAccessId", null).get();

        if (null != aWSAccessId) {
            return aWSAccessId.trim();
        }
        else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.eureka.EurekaServerConfig#getAWSSecretKey()
     */
    @Override
    public String getAWSSecretKey() {
        String aWSSecretKey = configInstance
        .getStringProperty(namespace + "awsSecretKey", null).get();

        if (null != aWSSecretKey) {
            return aWSSecretKey.trim();
        }
        else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.eureka.EurekaServerConfig#getEIPBindRebindRetries()
     */
    @Override
    public int getEIPBindRebindRetries() {
        return configInstance
        .getIntProperty(namespace + "eipBindRebindRetries", 3).get();
        
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.eureka.EurekaServerConfig#getEIPBindingRetryInterval()
     */
    @Override
    public int getEIPBindingRetryIntervalMs() {
        return configInstance
        .getIntProperty(namespace + "eipBindRebindRetryIntervalMs", (5 * 60 * 1000)).get();
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.eureka.EurekaServerConfig#shouldEnableSelfPreservation()
     */
    @Override
    public boolean shouldEnableSelfPreservation() {
        return configInstance
        .getBooleanProperty(namespace + "enableSelfPreservation", true).get();
    }

    /*
     * (non-Javadoc)
     * @see com.netflix.eureka.EurekaServerConfig#getPeerEurekaNodesUpdateInterval()
     */
    @Override
    public int getPeerEurekaNodesUpdateIntervalMs() {
        return configInstance
        .getIntProperty(namespace + "peerEurekaNodesUpdateIntervalMs", (10 * 60 * 1000)).get();
    }

    @Override
    public int getRenewalThresholdUpdateIntervalMs() {
        return configInstance
        .getIntProperty(namespace + "renewalThresholdUpdateIntervalMs", (15 * 60 * 1000)).get();
    }

    @Override
    public double getRenewalPercentThreshold() {
        return configInstance
        .getDoubleProperty(namespace + "renewalPercentThreshold", 0.85).get();
    }

    @Override
    public int getNumberOfReplicationRetries() {
        return configInstance
        .getIntProperty(namespace + "numberOfReplicationRetries", 5).get();
    }

    @Override
    public boolean shouldReplicateOnlyIfUP() {
        return configInstance
        .getBooleanProperty(namespace + "replicateOnlyIfUP", true).get();
    }

    @Override
    public int getPeerEurekaStatusRefreshTimeIntervalMs() {
        return configInstance
        .getIntProperty(namespace + "peerEurekaStatusRefreshTimeIntervalMs", (30 * 1000)).get();
    }

    @Override
    public int getWaitTimeInMsWhenSyncEmpty() {
        return configInstance
        .getIntProperty(namespace + "waitTimeInMsWhenSyncEmpty", (1000 * 60 * 5)).get();
    }

    @Override
    public int getPeerNodeConnectTimeoutMs() {
        return configInstance
        .getIntProperty(namespace + "peerNodeConnectTimeoutMs", 200).get();
    }

    @Override
    public int getPeerNodeReadTimeoutMs() {
        return configInstance
        .getIntProperty(namespace + "peerNodeReadTimeoutMs", 200).get();
    }

    @Override
    public int getPeerNodeTotalConnections() {
        return configInstance
        .getIntProperty(namespace + "peerNodeTotalConnections", 1000).get();
    }

    @Override
    public int getPeerNodeTotalConnectionsPerHost() {
        return configInstance
        .getIntProperty(namespace + "peerNodeTotalConnections", 500).get();
    }

    @Override
    public int getPeerNodeConnectionIdleTimeoutSeconds() {
        return configInstance
        .getIntProperty(namespace + "peerNodeConnectionIdleTimeoutSeconds", 30).get();
    }

    @Override
    public boolean shouldRetryIndefinitelyToReplicateStatus() {
        return configInstance
        .getBooleanProperty(namespace + "retryIndefinitelyToReplicateStatus", true).get();
    }

    @Override
    public long getRetentionTimeInMSInDeltaQueue() {
        return configInstance
        .getLongProperty(namespace + "retentionTimeInMSInDeltaQueue", (3 * 60 * 1000)).get();
    }

    @Override
    public long getDeltaRetentionTimerIntervalInMs() {
        return configInstance
        .getLongProperty(namespace + "deltaRetentionTimerIntervalInMs", (30 * 1000)).get();
    }

    @Override
    public long getEvictionIntervalTimerInMs() {
        return configInstance
        .getLongProperty(namespace + "evictionIntervalTimerInMs", (60 * 1000)).get();
    }

    @Override
    public int getASGQueryTimeoutMs() {
        return configInstance
        .getIntProperty(namespace + "asgQueryTimeoutMs", 1000).get();
    }

    @Override
    public long getASGUpdateIntervalMs() {
        return configInstance
        .getIntProperty(namespace + "asgUpdateIntervalMs", (60 * 1000)).get();
    }

    @Override
    public long getResponseCacheAutoExpirationInSeconds() {
        return configInstance
        .getIntProperty(namespace + "responseCacheAutoExpirationInSeconds", 180).get();
    }

    @Override
    public long getResponseCacheUpdateIntervalMs() {
        return configInstance
        .getIntProperty(namespace + "responseCacheUpdateIntervalMs", (30 * 1000)).get();
    }

    @Override
    public boolean shouldDisableDelta() {
        return configInstance
        .getBooleanProperty(namespace + "disableDelta", false).get();
    }

    @Override
    public long getMaxIdleThreadInMinutesAgeForStatusReplication() {
        return configInstance
        .getLongProperty(namespace + "maxIdleThreadAgeInMinutesForStatusReplication", 10).get();
    }

    @Override
    public int getMinThreadsForStatusReplication() {
        return configInstance
        .getIntProperty(namespace + "minThreadsForStatusReplication", 1).get();
    }

    @Override
    public int getMaxThreadsForStatusReplication() {
        return configInstance
        .getIntProperty(namespace + "maxThreadsForStatusReplication", 1).get();
    }

    @Override
    public int getMaxElementsInStatusReplicationPool() {
        return configInstance
        .getIntProperty(namespace + "maxElementsInStatusReplicationPool", 10000).get();
    }

    @Override
    public int getMaxElementsInReplicationPool() {
        return configInstance
        .getIntProperty(namespace + "maxElementsInReplicationPool", 120).get();
    }

    @Override
    public long getMaxIdleThreadAgeInMinutesForReplication() {
        return configInstance
        .getIntProperty(namespace + "maxIdleThreadAgeInMinutesForReplication", 15).get();
    }

    @Override
    public int getMinThreadsForReplication() {
        return configInstance
        .getIntProperty(namespace + "minThreadsForReplication", 20).get();
     }

    @Override
    public int getMaxThreadsForReplication() {
        return configInstance
        .getIntProperty(namespace + "maxThreadsForReplication", 60).get();
    }

    @Override
    public boolean shouldSyncWhenTimestampDiffers() {
        return configInstance
        .getBooleanProperty(namespace + "syncWhenTimestampDiffers", true).get();
    }

    @Override
    public int getRegistrySyncRetries() {
        return configInstance
        .getIntProperty(namespace + "numberRegistrySyncRetries", 5).get();
    }

}
