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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

import javax.annotation.Nullable;

/**
 * 
 * A default implementation of eureka server configuration as required by
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
    private static final int TIME_TO_WAIT_FOR_REPLICATION = 30000;

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
     * 
     * @see com.netflix.eureka.EurekaServerConfig#getAWSAccessId()
     */
    @Override
    public String getAWSAccessId() {
        String aWSAccessId = configInstance.getStringProperty(
                namespace + "awsAccessId", null).get();

        if (null != aWSAccessId) {
            return aWSAccessId.trim();
        } else {
            return null;
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.EurekaServerConfig#getAWSAccessId()
     */
    @Override
    public String getAWSSecretKey() {
        String aWSSecretKey = configInstance.getStringProperty(
                namespace + "awsSecretKey", null).get();

        if (null != aWSSecretKey) {
            return aWSSecretKey.trim();
        } else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.EurekaServerConfig#getEIPBindRebindRetries()
     */
    @Override
    public int getEIPBindRebindRetries() {
        return configInstance.getIntProperty(
                namespace + "eipBindRebindRetries", 3).get();

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.EurekaServerConfig#getEIPBindingRetryInterval()
     */
    @Override
    public int getEIPBindingRetryIntervalMs() {
        return configInstance.getIntProperty(
                namespace + "eipBindRebindRetryIntervalMs", (5 * 60 * 1000))
                .get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.eureka.EurekaServerConfig#shouldEnableSelfPreservation()
     */
    @Override
    public boolean shouldEnableSelfPreservation() {
        return configInstance.getBooleanProperty(
                namespace + "enableSelfPreservation", true).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.eureka.EurekaServerConfig#getPeerEurekaNodesUpdateInterval()
     */
    @Override
    public int getPeerEurekaNodesUpdateIntervalMs() {
        return configInstance
        .getIntProperty(namespace + "peerEurekaNodesUpdateIntervalMs",
                (10 * 60 * 1000)).get();
    }

    @Override
    public int getRenewalThresholdUpdateIntervalMs() {
        return configInstance.getIntProperty(
                namespace + "renewalThresholdUpdateIntervalMs",
                (15 * 60 * 1000)).get();
    }

    @Override
    public double getRenewalPercentThreshold() {
        return configInstance.getDoubleProperty(
                namespace + "renewalPercentThreshold", 0.85).get();
    }

    @Override
    public int getNumberOfReplicationRetries() {
        return configInstance.getIntProperty(
                namespace + "numberOfReplicationRetries", 5).get();
    }

    @Override
    public int getPeerEurekaStatusRefreshTimeIntervalMs() {
        return configInstance.getIntProperty(
                namespace + "peerEurekaStatusRefreshTimeIntervalMs",
                (30 * 1000)).get();
    }

    @Override
    public int getWaitTimeInMsWhenSyncEmpty() {
        return configInstance.getIntProperty(
                namespace + "waitTimeInMsWhenSyncEmpty", (1000 * 60 * 5)).get();
    }

    @Override
    public int getPeerNodeConnectTimeoutMs() {
        return configInstance.getIntProperty(
                namespace + "peerNodeConnectTimeoutMs", 200).get();
    }

    @Override
    public int getPeerNodeReadTimeoutMs() {
        return configInstance.getIntProperty(
                namespace + "peerNodeReadTimeoutMs", 200).get();
    }

    @Override
    public int getPeerNodeTotalConnections() {
        return configInstance.getIntProperty(
                namespace + "peerNodeTotalConnections", 1000).get();
    }

    @Override
    public int getPeerNodeTotalConnectionsPerHost() {
        return configInstance.getIntProperty(
                namespace + "peerNodeTotalConnections", 500).get();
    }

    @Override
    public int getPeerNodeConnectionIdleTimeoutSeconds() {
        return configInstance.getIntProperty(
                namespace + "peerNodeConnectionIdleTimeoutSeconds", 30).get();
    }

    @Override
    public long getRetentionTimeInMSInDeltaQueue() {
        return configInstance.getLongProperty(
                namespace + "retentionTimeInMSInDeltaQueue", (3 * 60 * 1000))
                .get();
    }

    @Override
    public long getDeltaRetentionTimerIntervalInMs() {
        return configInstance.getLongProperty(
                namespace + "deltaRetentionTimerIntervalInMs", (30 * 1000))
                .get();
    }

    @Override
    public long getEvictionIntervalTimerInMs() {
        return configInstance.getLongProperty(
                namespace + "evictionIntervalTimerInMs", (60 * 1000)).get();
    }

    @Override
    public int getASGQueryTimeoutMs() {
        return configInstance.getIntProperty(namespace + "asgQueryTimeoutMs",
                300).get();
    }

    @Override
    public long getASGUpdateIntervalMs() {
        return configInstance.getIntProperty(namespace + "asgUpdateIntervalMs",
                (5 * 60 * 1000)).get();
    }

    @Override
    public long getResponseCacheAutoExpirationInSeconds() {
        return configInstance.getIntProperty(
                namespace + "responseCacheAutoExpirationInSeconds", 180).get();
    }

    @Override
    public long getResponseCacheUpdateIntervalMs() {
        return configInstance.getIntProperty(
                namespace + "responseCacheUpdateIntervalMs", (30 * 1000)).get();
    }

    @Override
    public boolean shouldDisableDelta() {
        return configInstance.getBooleanProperty(namespace + "disableDelta",
                false).get();
    }

    @Override
    public long getMaxIdleThreadInMinutesAgeForStatusReplication() {
        return configInstance
        .getLongProperty(
                namespace
                + "maxIdleThreadAgeInMinutesForStatusReplication",
                10).get();
    }

    @Override
    public int getMinThreadsForStatusReplication() {
        return configInstance.getIntProperty(
                namespace + "minThreadsForStatusReplication", 1).get();
    }

    @Override
    public int getMaxThreadsForStatusReplication() {
        return configInstance.getIntProperty(
                namespace + "maxThreadsForStatusReplication", 1).get();
    }

    @Override
    public int getMaxElementsInStatusReplicationPool() {
        return configInstance.getIntProperty(
                namespace + "maxElementsInStatusReplicationPool", 10000).get();
    }

    @Override
    public boolean shouldSyncWhenTimestampDiffers() {
        return configInstance.getBooleanProperty(
                namespace + "syncWhenTimestampDiffers", true).get();
    }

    @Override
    public int getRegistrySyncRetries() {
        return configInstance.getIntProperty(
                namespace + "numberRegistrySyncRetries", 5).get();
    }

    @Override
    public int getMaxElementsInPeerReplicationPool() {
        return configInstance.getIntProperty(
                namespace + "maxElementsInPeerReplicationPool", 10000).get();
    }

    @Override
    public long getMaxIdleThreadAgeInMinutesForPeerReplication() {
        return configInstance.getIntProperty(
                namespace + "maxIdleThreadAgeInMinutesForPeerReplication", 15)
                .get();
    }

    @Override
    public int getMinThreadsForPeerReplication() {
        return configInstance.getIntProperty(
                namespace + "minThreadsForPeerReplication", 5).get();
    }

    @Override
    public int getMaxThreadsForPeerReplication() {
        return configInstance.getIntProperty(
                namespace + "maxThreadsForPeerReplication", 20).get();
    }

    @Override
    public int getMaxTimeForReplication() {
        return configInstance.getIntProperty(
                namespace + "maxTimeForReplication",
                TIME_TO_WAIT_FOR_REPLICATION).get();
    }

    @Override
    public boolean shouldPrimeAwsReplicaConnections() {
        return configInstance.getBooleanProperty(
                namespace + "primeAwsReplicaConnections", true).get();
    }

    @Override
    public boolean shouldDisableDeltaForRemoteRegions() {
        return configInstance.getBooleanProperty(
                namespace + "disableDeltaForRemoteRegions", false).get();
    }

    @Override
    public int getRemoteRegionConnectTimeoutMs() {
        return configInstance.getIntProperty(
                namespace + "remoteRegionConnectTimeoutMs", 1000).get();
    }

    @Override
    public int getRemoteRegionReadTimeoutMs() {
        return configInstance.getIntProperty(
                namespace + "remoteRegionReadTimeoutMs", 1000).get();
    }

    @Override
    public int getRemoteRegionTotalConnections() {
        return configInstance.getIntProperty(
                namespace + "remoteRegionTotalConnections", 1000).get();
    }

    @Override
    public int getRemoteRegionTotalConnectionsPerHost() {
        return configInstance.getIntProperty(
                namespace + "remoteRegionTotalConnections", 500).get();
    }

    @Override
    public int getRemoteRegionConnectionIdleTimeoutSeconds() {
        return configInstance.getIntProperty(
                namespace + "remoteRegionConnectionIdleTimeoutSeconds", 30)
                .get();
    }

    @Override
    public boolean shouldGZipContentFromRemoteRegion() {
        return configInstance.getBooleanProperty(
                namespace + "remoteRegion.gzipContent", true).get();
    }

    /**
     * Expects a property with name: [eureka-namespace].remoteRegionUrlsWithName and a value being a comma separated list
     * of region name & remote url pairs, separated with a ";". <br/>
     * So, if you wish to specify two regions with name region1 & region2, the property value will be:
     <PRE>
        eureka.remoteRegionUrlsWithName=region1;http://region1host/eureka/v2,region2;http://region2host/eureka/v2
     </PRE>
     * The above property will result in the following map:
     <PRE>
        region1->"http://region1host/eureka/v2"
        region2->"http://region2host/eureka/v2"
     </PRE>
     * @return A map of region name to remote region URL parsed from the property specified above. If there is no
     * property available, then an empty map is returned.
     */
    @Override
    public Map<String, String> getRemoteRegionUrlsWithName() {
        String propName = namespace + "remoteRegionUrlsWithName";
        String remoteRegionUrlWithNameString = configInstance.getStringProperty(propName, null).get();
        if (null == remoteRegionUrlWithNameString) {
            return Collections.emptyMap();
        }

        String[] remoteRegionUrlWithNamePairs = remoteRegionUrlWithNameString.split(",");
        Map<String, String> toReturn = new HashMap<String, String>(remoteRegionUrlWithNamePairs.length);

        final String pairSplitChar = ";";
        for (String remoteRegionUrlWithNamePair : remoteRegionUrlWithNamePairs) {
            String[] pairSplit = remoteRegionUrlWithNamePair.split(pairSplitChar);
            if (pairSplit.length < 2) {
                logger.error("Error reading eureka remote region urls from property {}. " +
                             "Invalid entry {} for remote region url. The entry must contain region name and url separated by a {}. Ignoring this entry.",
                             new String[]{propName, remoteRegionUrlWithNamePair, pairSplitChar});
            } else {
                String regionName = pairSplit[0];
                String regionUrl = pairSplit[1];
                if (pairSplit.length > 2) {
                    StringBuilder regionUrlAssembler = new StringBuilder();
                    for (int i = 1; i < pairSplit.length; i++) {
                        if (regionUrlAssembler.length() != 0) {
                            regionUrlAssembler.append(pairSplitChar);
                        }
                        regionUrlAssembler.append(pairSplit[i]);
                    }
                    regionUrl = regionUrlAssembler.toString();
                }
                toReturn.put(regionName, regionUrl);
            }
        }
        return toReturn;
    }

    @Override
    public String[] getRemoteRegionUrls() {
        String remoteRegionUrlString = configInstance.getStringProperty(
                namespace + "remoteRegionUrls", null).get();
        String[] remoteRegionUrl = null;
        if (remoteRegionUrlString != null) {
            remoteRegionUrl = remoteRegionUrlString.split(",");
        }
        return remoteRegionUrl;
    }

    @Nullable
    @Override
    public Set<String> getRemoteRegionAppWhitelist(@Nullable String regionName) {
        if (null == regionName) {
            regionName = "global";
        } else {
            regionName = regionName.trim().toLowerCase();
        }
        DynamicStringProperty appWhiteListProp =
                configInstance.getStringProperty(namespace + "remoteRegion." + regionName + ".appWhiteList", null);
        if (null == appWhiteListProp || null == appWhiteListProp.get()) {
            return null;
        } else {
            String appWhiteListStr = appWhiteListProp.get();
            String[] whitelistEntries = appWhiteListStr.split(",");
            return new HashSet<String>(Arrays.asList(whitelistEntries));
        }
    }

    @Override
    public int getRemoteRegionRegistryFetchInterval() {
        return configInstance.getIntProperty(
                namespace + "remoteRegion.registryFetchIntervalInSeconds", 30)
                .get();
    }

    @Override
    public String getRemoteRegionTrustStore() {
        return configInstance.getStringProperty(
                namespace + "remoteRegion.trustStoreFileName", "").get();

    }

    @Override
    public String getRemoteRegionTrustStorePassword() {
        return configInstance.getStringProperty(
                namespace + "remoteRegion.trustStorePassword", "changeit")
                .get();
    }

    @Override
    public boolean disableTransparentFallbackToOtherRegion() {
        return configInstance.getBooleanProperty(namespace + "remoteRegion.disable.transparent.fallback", false).get();
    }
}
