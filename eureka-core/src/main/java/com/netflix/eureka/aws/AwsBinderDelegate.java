package com.netflix.eureka.aws;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AwsBinderDelegate implements AwsBinder {

    private final AwsBinder delegate;

    @Inject
    public AwsBinderDelegate(EurekaServerConfig serverConfig,
                             EurekaClientConfig clientConfig,
                             PeerAwareInstanceRegistry registry,
                             ApplicationInfoManager applicationInfoManager) {
        AwsBindingStrategy bindingStrategy = serverConfig.getBindingStrategy();
        switch (bindingStrategy) {
            case ROUTE53:
                delegate = new Route53Binder(serverConfig, clientConfig, applicationInfoManager);
                break;
            case EIP:
                delegate = new EIPManager(serverConfig, clientConfig, registry, applicationInfoManager);
                break;
            case ENI:
                delegate = new ElasticNetworkInterfaceBinder(serverConfig, clientConfig, registry, applicationInfoManager);
                break;
            default:
                throw new IllegalArgumentException("Unexpected BindingStrategy " + bindingStrategy);
        }
    }

    @Override
    @PostConstruct
    public void start() {
        try {
            delegate.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @PreDestroy
    public void shutdown()  {
        try {
            delegate.shutdown();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}