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
package com.netflix.appinfo;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.config.ConfigurationManager;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.config.DynamicStringProperty;

/**
 * A properties based {@link InstanceInfo} configuration.
 * 
 * <p>
 * The information required for registration with eureka server is provided in a
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
public abstract class PropertiesInstanceConfig extends AbstractInstanceConfig
implements EurekaInstanceConfig {
    private static final String TEST = "test";
    private static final String ARCHAIUS_DEPLOYMENT_ENVIRONMENT = "archaius.deployment.environment";
    private static final String EUREKA_ENVIRONMENT = "eureka.environment";
    private static final Logger logger = LoggerFactory
    .getLogger(PropertiesInstanceConfig.class);
    protected String namespace = "eureka.";
    private static final DynamicStringProperty EUREKA_PROPS_FILE = DynamicPropertyFactory
    .getInstance().getStringProperty("eureka.client.props",
    "eureka-client");
    private static final DynamicPropertyFactory INSTANCE = com.netflix.config.DynamicPropertyFactory
    .getInstance();
    private static final String UNKNOWN_APPLICATION = "unknown";

    
    private static final String DEFAULT_STATUSPAGE_URLPATH = "/Status";
    private static final String DEFAULT_HOMEPAGE_URLPATH = "/";
    private static final String DEFAULT_HEALTHCHECK_URLPATH = "/healthcheck";
    
    private String propSecurePort = namespace + "securePort";
    private String propSecurePortEnabled = propSecurePort + ".enabled";
    private String propNonSecurePort;
    private String propName;
    private String propPortEnabled;
    private String propLeaseRenewalIntervalInSeconds;
    private String propLeaseExpirationDurationInSeconds;
    private String propSecureVirtualHostname;
    private String propVirtualHostname;
    private String propMetadataNamespace;
    private String propASGName;

    public PropertiesInstanceConfig() {
        init(namespace);
    }

    public PropertiesInstanceConfig(String namespace, DataCenterInfo info) {
        super(info);
        init(namespace);
    }

    public PropertiesInstanceConfig(String namespace) {
        init(namespace);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#isInstanceEnabledOnit()
     */
    @Override
    public boolean isInstanceEnabledOnit() {
        return INSTANCE.getBooleanProperty(namespace + "traffic.enabled",
                super.isInstanceEnabledOnit()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#getNonSecurePort()
     */
    @Override
    public int getNonSecurePort() {
        return INSTANCE.getIntProperty(propNonSecurePort,
                super.getNonSecurePort()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#getSecurePort()
     */
    @Override
    public int getSecurePort() {
        return INSTANCE.getIntProperty(propSecurePort, super.getSecurePort())
        .get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#isNonSecurePortEnabled()
     */
    @Override
    public boolean isNonSecurePortEnabled() {
        return INSTANCE.getBooleanProperty(propPortEnabled,
                super.isNonSecurePortEnabled()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#getSecurePortEnabled()
     */
    @Override
    public boolean getSecurePortEnabled() {
        return INSTANCE.getBooleanProperty(propSecurePortEnabled,
                super.getSecurePortEnabled()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.appinfo.AbstractInstanceConfig#getLeaseRenewalIntervalInSeconds
     * ()
     */
    @Override
    public int getLeaseRenewalIntervalInSeconds() {
        return INSTANCE.getIntProperty(propLeaseRenewalIntervalInSeconds,
                super.getLeaseRenewalIntervalInSeconds()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#
     * getLeaseExpirationDurationInSeconds()
     */
    @Override
    public int getLeaseExpirationDurationInSeconds() {
        return INSTANCE.getIntProperty(propLeaseExpirationDurationInSeconds,
                super.getLeaseExpirationDurationInSeconds()).get();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#getVirtualHostName()
     */
    @Override
    public String getVirtualHostName() {
        if (this.isNonSecurePortEnabled()) {
            return INSTANCE.getStringProperty(propVirtualHostname,
                    super.getVirtualHostName()).get();
        } else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.netflix.appinfo.AbstractInstanceConfig#getSecureVirtualHostName()
     */
    @Override
    public String getSecureVirtualHostName() {
        if (this.getSecurePortEnabled()) {
            return INSTANCE.getStringProperty(propSecureVirtualHostname,
                    super.getSecureVirtualHostName()).get();
        } else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#getASGName()
     */
    @Override
    public String getASGName() {
        return INSTANCE.getStringProperty(propASGName, super.getASGName())
        .get();
    }

    /**
     * Gets the metadata map associated with the instance. The properties that
     * will be looked up for this will be <code>namespace + ".metadata"</code>.
     * 
     * <p>
     * For instance, if the given namespace is <code>eureka.appinfo</code>, the
     * metadata keys are searched under the namespace
     * <code>eureka.appinfo.metadata</code>.
     * </p>
     */
    @Override
    public Map<String, String> getMetadataMap() {
        Map<String, String> metadataMap = new LinkedHashMap<String, String>();
        Configuration config = (Configuration) INSTANCE
        .getBackingConfigurationSource();
        for (Iterator<String> iter = config.subset(propMetadataNamespace)
                .getKeys();

        iter.hasNext();) {

            String key = iter.next();
            String value = config.getString(propMetadataNamespace + key);
            metadataMap.put(key, value);
        }
        return metadataMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#getAppname()
     */
    @Override
    public String getAppname() {
        return INSTANCE.getStringProperty(propName, UNKNOWN_APPLICATION).get()
        .trim();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.netflix.appinfo.AbstractInstanceConfig#getIpAddress()
     */
    public String getIpAddress() {
        return super.getIpAddress();
    }
    

    @Override
    public String getStatusPageUrlPath() {
                return INSTANCE.getStringProperty(namespace + "statusPageUrlPath",
                        DEFAULT_STATUSPAGE_URLPATH).get();
    }

    @Override
    public String getStatusPageUrl() {
       return INSTANCE.getStringProperty(namespace + "statusPageUrl", null)
        .get();
    }


    @Override
    public String getHomePageUrlPath() {
        return INSTANCE.getStringProperty(namespace + "homePageUrlPath",
            DEFAULT_HOMEPAGE_URLPATH).get();
    }
 
    @Override
    public String getHomePageUrl() {
        return INSTANCE.getStringProperty(namespace + "homePageUrl", null)
                .get();
    }
    @Override
    public String getHealthCheckUrlPath() {
        return INSTANCE.getStringProperty( namespace + "healthCheckUrlPath",
                DEFAULT_HEALTHCHECK_URLPATH).get();
    }
    
    @Override
    public String getHealthCheckUrl() {
        return INSTANCE.getStringProperty(namespace + "healthCheckUrl", null)
        .get();
     }
   
    @Override
    public String getSecureHealthCheckUrl() {
        return INSTANCE.getStringProperty(namespace + "secureHealthCheckUrl",
                null).get(); 
     }
    
    @Override
    public String getNamespace() {
        return this.namespace;
    }
   

    private void init(String namespace) {
        this.namespace = namespace;
        propSecurePort = namespace + "securePort";
        propSecurePortEnabled = propSecurePort + ".enabled";
        propNonSecurePort = namespace + "port";

        propName = namespace + "name";
        propPortEnabled = propNonSecurePort + ".enabled";
        propLeaseRenewalIntervalInSeconds = namespace + "lease.renewalInterval";
        propLeaseExpirationDurationInSeconds = namespace + "lease.duration";
        propSecureVirtualHostname = namespace + "secureVipAddress";
        propVirtualHostname = namespace + "vipAddress";
        propMetadataNamespace = namespace + "metadata.";
        propASGName = namespace + "asgName";
        String env = ConfigurationManager.getConfigInstance().getString(
                EUREKA_ENVIRONMENT, TEST);
        ConfigurationManager.getConfigInstance().setProperty(
                ARCHAIUS_DEPLOYMENT_ENVIRONMENT, env);
        String eurekaPropsFile = EUREKA_PROPS_FILE.get();
        try {
            ConfigurationManager
            .loadCascadedPropertiesFromResources(eurekaPropsFile);
        } catch (IOException e) {
            logger.warn(
                    "Cannot find the properties specified : {}. This may be okay if there are other environment specific properties or the configuration is installed with a different mechanism.",
                    eurekaPropsFile);

        }
    }
  
}
