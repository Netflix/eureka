package com.netflix.appinfo.providers;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AmazonInfoConfig;
import com.netflix.appinfo.Archaius2AmazonInfoConfig;
import com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.CommonConstants;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.internal.util.AmazonInfoUtils;
import com.netflix.discovery.internal.util.InternalPrefixedConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.SocketTimeoutException;
import java.net.URL;

/**
 * A factory for {@link com.netflix.appinfo.EurekaInstanceConfig} that can provide either
 * {@link com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig} or
 * {@link com.netflix.appinfo.EurekaArchaius2InstanceConfig} based on some selection strategy.
 *
 * If no config based override is applied, this Factory will automatically detect whether the
 * current deployment environment is EC2 or not, and create the appropriate Config instances.
 *
 * Setting the property <b>eureka.instanceDeploymentEnvironment=ec2</b> will force the instantiation
 * of {@link com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig}, regardless of what the
 * automatic environment detection says.
 *
 * Setting the property <b>eureka.instanceDeploymentEnvironment={a non-null, non-ec2 string}</b>
 * will force the instantiation of {@link com.netflix.appinfo.EurekaArchaius2InstanceConfig},
 * regardless of what the automatic environment detection says.
 *
 * Why define the {@link com.netflix.appinfo.providers.EurekaInstanceConfigFactory} instead
 * of using {@link javax.inject.Provider} instead? Provider does not work due to the fact that
 * Guice treats Providers specially.
 *
 * @author David Liu
 */
@Singleton
@ConfigurationSource(CommonConstants.CONFIG_FILE_NAME)
public class CompositeInstanceConfigFactory implements EurekaInstanceConfigFactory {
    private static final Logger logger = LoggerFactory.getLogger(CompositeInstanceConfigFactory.class);

    private static final String DEPLOYMENT_ENVIRONMENT_OVERRIDE_KEY = "instanceDeploymentEnvironment";

    private final String namespace;
    private final Config configInstance;
    private final InternalPrefixedConfig prefixedConfig;

    private EurekaInstanceConfig eurekaInstanceConfig;

    @Inject
    public CompositeInstanceConfigFactory(Config configInstance, String namespace) {
        this.configInstance = configInstance;
        this.namespace = namespace;
        this.prefixedConfig = new InternalPrefixedConfig(configInstance, namespace);
    }

    @Override
    public synchronized EurekaInstanceConfig get() {
        if (eurekaInstanceConfig == null) {
            // create the amazonInfoConfig before we can determine if we are in EC2, as we want to use the amazonInfoConfig for
            // that determination. This is just the config however so is cheap to do and does not have side effects.
            AmazonInfoConfig amazonInfoConfig = new Archaius2AmazonInfoConfig(configInstance, namespace);
            if (isInEc2(amazonInfoConfig)) {
                eurekaInstanceConfig = new Ec2EurekaArchaius2InstanceConfig(configInstance, amazonInfoConfig, namespace);
                logger.info("Creating EC2 specific instance config");
            } else {
                eurekaInstanceConfig = new EurekaArchaius2InstanceConfig(configInstance, namespace);
                logger.info("Creating generic instance config");
            }

            // TODO: Remove this when DiscoveryManager is finally no longer used
            DiscoveryManager.getInstance().setEurekaInstanceConfig(eurekaInstanceConfig);
        }

        return eurekaInstanceConfig;
    }


    private boolean isInEc2(AmazonInfoConfig amazonInfoConfig) {
        String deploymentEnvironmentOverride = getDeploymentEnvironmentOverride();
        if (deploymentEnvironmentOverride == null) {
            return autoDetectEc2(amazonInfoConfig);
        } else if ("ec2".equalsIgnoreCase(deploymentEnvironmentOverride)) {
            logger.info("Assuming EC2 deployment environment due to config override");
            return true;
        } else {
            return false;
        }
    }

    // best effort try to determine if we are in ec2 by trying to read the instanceId from metadata url
    private boolean autoDetectEc2(AmazonInfoConfig amazonInfoConfig) {
        try {
            URL url = AmazonInfo.MetaDataKey.instanceId.getURL(null, null);
            String id = AmazonInfoUtils.readEc2MetadataUrl(
                    AmazonInfo.MetaDataKey.instanceId,
                    url,
                    amazonInfoConfig.getConnectTimeout(),
                    amazonInfoConfig.getReadTimeout()
            );

            if (id != null) {
                logger.info("Auto detected EC2 deployment environment, instanceId = {}", id);
                return true;
            } else {
                logger.info("Auto detected non-EC2 deployment environment, instanceId from metadata url is null");
                return false;
            }
        } catch (SocketTimeoutException e) {
            logger.info("Auto detected non-EC2 deployment environment, connection to ec2 instance metadata url failed.");
        } catch (Exception e) {
            logger.warn("Failed to auto-detect whether we are in EC2 due to unexpected exception", e);
        }
        return false;
    }

    private String getDeploymentEnvironmentOverride() {
        return prefixedConfig.getString(DEPLOYMENT_ENVIRONMENT_OVERRIDE_KEY, null);
    }
}
