package com.netflix.discovery.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.Archaius2VipAddressResolver;
import com.netflix.appinfo.providers.CompositeInstanceConfigFactory;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.appinfo.providers.EurekaInstanceConfigFactory;
import com.netflix.appinfo.providers.VipAddressResolver;
import com.netflix.archaius.api.Config;
import com.netflix.archaius.api.annotations.ConfigurationSource;
import com.netflix.discovery.AbstractDiscoveryClientOptionalArgs;
import com.netflix.discovery.CommonConstants;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.EurekaArchaius2ClientConfig;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.shared.resolver.EndpointRandomizer;
import com.netflix.discovery.shared.resolver.ResolverUtils;
import com.netflix.discovery.shared.transport.EurekaArchaius2TransportConfig;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.discovery.shared.transport.jersey.Jersey1DiscoveryClientOptionalArgs;

import javax.inject.Named;
import javax.inject.Singleton;

final class InternalEurekaClientModule extends AbstractModule {

    static final String INSTANCE_CONFIG_NAMESPACE_KEY = "eureka.instance.config.namespace";
    static final String CLIENT_CONFIG_NAMESPACE_KEY = "eureka.client.config.namespace";

    @Singleton
    static class ModuleConfig {
        @Inject
        Config config;

        @Inject(optional = true)
        @Named(InternalEurekaClientModule.INSTANCE_CONFIG_NAMESPACE_KEY)
        String instanceConfigNamespace;

        @Inject(optional = true)
        @Named(InternalEurekaClientModule.CLIENT_CONFIG_NAMESPACE_KEY)
        String clientConfigNamespace;

        @Inject(optional = true)
        EurekaInstanceConfigFactory instanceConfigFactory;

        String getInstanceConfigNamespace() {
            return instanceConfigNamespace == null ? "eureka" : instanceConfigNamespace;
        }

        String getClientConfigNamespace() {
            return clientConfigNamespace == null ? "eureka" : clientConfigNamespace;
        }

        EurekaInstanceConfigFactory getInstanceConfigProvider() {
            return instanceConfigFactory == null
                    ? new CompositeInstanceConfigFactory(config, getInstanceConfigNamespace())
                    : instanceConfigFactory;
        }
    }

    @Override
    protected void configure() {
        // require binding for Config from archaius2
        requireBinding(Config.class);

        bind(ApplicationInfoManager.class).asEagerSingleton();

        bind(VipAddressResolver.class).to(Archaius2VipAddressResolver.class);
        bind(InstanceInfo.class).toProvider(EurekaConfigBasedInstanceInfoProvider.class);
        bind(EurekaClient.class).to(DiscoveryClient.class);
        bind(EndpointRandomizer.class).toInstance(ResolverUtils::randomize);


        // Default to the jersey1 discovery client optional args
        bind(AbstractDiscoveryClientOptionalArgs.class).to(Jersey1DiscoveryClientOptionalArgs.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    public EurekaTransportConfig getEurekaTransportConfig(Config config, ModuleConfig moduleConfig, EurekaConfigLoader configLoader) {
        return new EurekaArchaius2TransportConfig(config, moduleConfig.getClientConfigNamespace());
    }

    @Provides
    @Singleton
    public EurekaClientConfig getEurekaClientConfig(Config config, EurekaTransportConfig transportConfig, ModuleConfig moduleConfig, EurekaConfigLoader configLoader) {
        return new EurekaArchaius2ClientConfig(config, transportConfig, moduleConfig.getClientConfigNamespace());
    }

    @Provides
    @Singleton
    public EurekaInstanceConfig getEurekaInstanceConfigProvider(ModuleConfig moduleConfig, EurekaConfigLoader configLoader) {
        return moduleConfig.getInstanceConfigProvider().get();
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && getClass().equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }


    // need this internal class to ensure config file loading happens
    @ConfigurationSource(CommonConstants.CONFIG_FILE_NAME)
    private static class EurekaConfigLoader {

    }
}
