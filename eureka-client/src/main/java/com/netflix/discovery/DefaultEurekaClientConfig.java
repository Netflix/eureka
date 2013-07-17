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

package com.netflix.discovery;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

import javax.annotation.Nullable;

/**
 * 
 * A default implementation of eureka client configuration as required by
 * {@link EurekaClientConfig}.
 * 
 * <p>
 * The information required for configuring eureka client is provided in a
 * configuration file.The configuration file is searched for in the classpath
 * with the name specified by the property <em>eureka.client.props</em> and with
 * the suffix <em>.properties</em>. If the property is not specified,
 * <em>eureka-client.properties</em> is assumed as the default.The properties
 * that are looked up uses the <em>namespace</em> passed on to this class.
 * </p>
 * 
 * <p>
 * If the <em>eureka.environment</em> property is specified, additionally
 * <em>eureka-client-<eureka.environment>.properties</em> is loaded in addition
 * to <em>eureka-client.properties</em>.
 * </p>
 * 
 * @author Karthik Ranganathan
 * 
 */
public class DefaultEurekaClientConfig implements EurekaClientConfig {
    private static final String ARCHAIUS_DEPLOYMENT_ENVIRONMENT = "archaius.deployment.environment";
    private static final String TEST = "test";
    private static final String EUREKA_ENVIRONMENT = "eureka.environment";
    private static final Logger logger = LoggerFactory
            .getLogger(DefaultEurekaClientConfig.class);
    private static final DynamicPropertyFactory configInstance = com.netflix.config.DynamicPropertyFactory
            .getInstance();
    private static final DynamicStringProperty EUREKA_PROPS_FILE = DynamicPropertyFactory
            .getInstance().getStringProperty("eureka.client.props",
                    "eureka-client");
    public static final String DEFAULT_ZONE = "defaultZone";
    private String namespace = "eureka.";

    public DefaultEurekaClientConfig() {
        init();
    }

    public DefaultEurekaClientConfig(String namespace) {
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
     * @see
     * com.netflix.discovery.EurekaClientConfig#getRegistryFetchIntervalSeconds
     * ()
     */
    @Override
    public int getRegistryFetchIntervalSeconds() {
        return configInstance.getIntProperty(
                namespace + "client.refresh.interval", 30).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#
     * getInstanceInfoReplicationIntervalSeconds()
     */
    @Override
    public int getInstanceInfoReplicationIntervalSeconds() {
        return configInstance.getIntProperty(
                namespace + "appinfo.replicate.interval", 30).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getDnsPollIntervalSeconds()
     */
    @Override
    public int getEurekaServiceUrlPollIntervalSeconds() {
        return configInstance.getIntProperty(
                namespace + "serviceUrlPollIntervalMs", 5 * 60 * 1000).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getProxyHost()
     */
    @Override
    public String getProxyHost() {
        return configInstance.getStringProperty(
                namespace + "eurekaServer.proxyHost", null).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getProxyPort()
     */
    @Override
    public String getProxyPort() {
        return configInstance.getStringProperty(
                namespace + "eurekaServer.proxyPort", null).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#shouldGZipContent()
     */
    @Override
    public boolean shouldGZipContent() {
        return configInstance.getBooleanProperty(
                namespace + "eurekaServer.gzipContent", true).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getDSServerReadTimeout()
     */
    @Override
    public int getEurekaServerReadTimeoutSeconds() {
        return configInstance.getIntProperty(
                namespace + "eurekaServer.readTimeout", 8000).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getDSServerConnectTimeout()
     */
    @Override
    public int getEurekaServerConnectTimeoutSeconds() {
        return configInstance.getIntProperty(
                namespace + "eurekaServer.connectTimeout", 5000).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getBackupRegistryImpl()
     */
    @Override
    public String getBackupRegistryImpl() {
        return configInstance.getStringProperty(namespace + "backupregistry",
                null).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.discovery.EurekaClientConfig#getDSServerTotalMaxConnections()
     */
    @Override
    public int getEurekaServerTotalConnections() {
        return configInstance.getIntProperty(
                namespace + "eurekaServer.maxTotalConnections", 200).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.discovery.EurekaClientConfig#getDSServerConnectionsPerHost()
     */
    @Override
    public int getEurekaServerTotalConnectionsPerHost() {
        return configInstance.getIntProperty(
                namespace + "eurekaServer.maxConnectionsPerHost", 50).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getDSServerURLContext()
     */
    @Override
    public String getEurekaServerURLContext() {
        return configInstance.getStringProperty(
                namespace + "eurekaServer.context",
                configInstance.getStringProperty(namespace + "context", null)
                        .get()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getDSServerPort()
     */
    @Override
    public String getEurekaServerPort() {
        return configInstance.getStringProperty(
                namespace + "eurekaServer.port",
                configInstance.getStringProperty(namespace + "port", null)
                        .get()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getDSServerDomain()
     */
    @Override
    public String getEurekaServerDNSName() {
        return configInstance.getStringProperty(
                namespace + "eurekaServer.domainName",
                configInstance
                        .getStringProperty(namespace + "domainName", null)
                        .get()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#shouldUseDns()
     */
    @Override
    public boolean shouldUseDnsForFetchingServiceUrls() {
        return configInstance.getBooleanProperty(namespace + "shouldUseDns",
                false).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.discovery.EurekaClientConfig#getDiscoveryRegistrationEnabled
     * ()
     */
    @Override
    public boolean shouldRegisterWithEureka() {
        return configInstance.getBooleanProperty(
                namespace + "registration.enabled", true).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#shouldPreferSameZoneDS()
     */
    @Override
    public boolean shouldPreferSameZoneEureka() {
        return configInstance.getBooleanProperty(namespace + "preferSameZone",
                true).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#shouldLogDeltaDiff()
     */
    @Override
    public boolean shouldLogDeltaDiff() {
        return configInstance.getBooleanProperty(
                namespace + "printDeltaFullDiff", false).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#shouldDisableDelta()
     */
    @Override
    public boolean shouldDisableDelta() {
        return configInstance.getBooleanProperty(namespace + "disableDelta",
                false).get();
    }

    @Nullable
    @Override
    public String fetchRegistryForRemoteRegions() {
        return configInstance.getStringProperty(namespace + "fetchRemoteRegionsRegistry", null).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getRegion()
     */
    @Override
    public String getRegion() {
        return configInstance.getStringProperty("eureka.region", "us-east-1")
                .get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.discovery.EurekaClientConfig#getAvailabilityZones()
     */
    @Override
    public String[] getAvailabilityZones(String region) {
        return configInstance
                .getStringProperty(
                        namespace + "" + region + ".availabilityZones",
                        DEFAULT_ZONE).get().split(",");
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.discovery.EurekaClientConfig#getEurekaServerServiceUrls()
     */
    @Override
    public List<String> getEurekaServerServiceUrls(String myZone) {
        String serviceUrls = configInstance.getStringProperty(
                namespace + "serviceUrl." + myZone, null).get();
        if (serviceUrls == null || serviceUrls.isEmpty()) {
            serviceUrls = configInstance.getStringProperty(
                    namespace + "serviceUrl." + "default", null).get();

        }
        if (serviceUrls != null) {
            return Arrays.asList(serviceUrls.split(","));
        }

        return new ArrayList<String>();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.discovery.EurekaClientConfig#shouldFilterOnlyUpInstances()
     */
    @Override
    public boolean shouldFilterOnlyUpInstances() {
        return configInstance.getBooleanProperty(
                namespace + "shouldFilterOnlyUpInstances", true).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.discovery.EurekaClientConfig#getEurekaConnectionIdleTimeout()
     */
    @Override
    public int getEurekaConnectionIdleTimeoutSeconds() {
        return configInstance.getIntProperty(
                namespace + "eurekaserver.connectionIdleTimeoutInSeconds", 30)
                .get();
    }

}
