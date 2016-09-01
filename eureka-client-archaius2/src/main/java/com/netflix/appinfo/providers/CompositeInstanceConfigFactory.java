package com.netflix.appinfo.providers;

import com.netflix.appinfo.AmazonInfoConfig;
import com.netflix.appinfo.Archaius2AmazonInfoConfig;
import com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaArchaius2InstanceConfig;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.DiscoveryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A factory for {@link com.netflix.appinfo.EurekaInstanceConfig} that can provide either
 * {@link com.netflix.appinfo.Ec2EurekaArchaius2InstanceConfig} or
 * {@link com.netflix.appinfo.EurekaArchaius2InstanceConfig} based on some selection strategy.
 *
 * Why define the {@link com.netflix.appinfo.providers.EurekaInstanceConfigFactory} instead
 * of using {@link javax.inject.Provider} instead? Provider does not work due to the fact that
 * Guice treats Providers specially.
 *
 * @author David Liu
 */
@Singleton
@ConfigurationSource("eureka-client")
public class CompositeInstanceConfigFactory implements EurekaInstanceConfigFactory {
    private static final Logger logger = LoggerFactory.getLogger(CompositeInstanceConfigFactory.class);

    private static final String INIT_WITH_EC2_INSTANCE_CONFIG_KEY = "shouldInitAsEc2";

    private final String namespace;
    private final Config config;

    private EurekaInstanceConfig eurekaInstanceConfig;

    @Inject
    public CompositeInstanceConfigFactory(Config config, String namespace) {
        this.config = config;
        this.namespace = namespace;
    }

    @Override
    public synchronized EurekaInstanceConfig get() {
        if (eurekaInstanceConfig == null) {
            if (isInEc2()) {
                AmazonInfoConfig amazonInfoConfig = new Archaius2AmazonInfoConfig(config, namespace);
                eurekaInstanceConfig = new Ec2EurekaArchaius2InstanceConfig(config, amazonInfoConfig, namespace);

                logger.info("Creating EC2 specific instance config");
            } else {
                eurekaInstanceConfig = new EurekaArchaius2InstanceConfig(config, namespace);

                logger.info("Creating generic instance config");
            }

            // TODO: Remove this when DiscoveryManager is finally no longer used
            DiscoveryManager.getInstance().setEurekaInstanceConfig(eurekaInstanceConfig);
        }

        return eurekaInstanceConfig;
    }

    private boolean isInEc2() {
        String result = config.getPrefixedView(namespace).getString(INIT_WITH_EC2_INSTANCE_CONFIG_KEY, "true");
        return !"false".equals(result);  // treat true as default
    }

}
