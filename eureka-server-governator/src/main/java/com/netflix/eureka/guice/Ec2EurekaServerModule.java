package com.netflix.eureka.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.netflix.eureka.DefaultEurekaServerConfig;
import com.netflix.eureka.DefaultEurekaServerContext;
import com.netflix.eureka.EurekaServerConfig;
import com.netflix.eureka.EurekaServerContext;
import com.netflix.eureka.aws.AwsBinderDelegate;
import com.netflix.eureka.cluster.PeerEurekaNodes;
import com.netflix.eureka.registry.AbstractInstanceRegistry;
import com.netflix.eureka.registry.AwsInstanceRegistry;
import com.netflix.eureka.registry.InstanceRegistry;
import com.netflix.eureka.registry.PeerAwareInstanceRegistry;
import com.netflix.eureka.resources.DefaultServerCodecs;
import com.netflix.eureka.resources.ServerCodecs;

/**
 * @author David Liu
 */
public class Ec2EurekaServerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(EurekaServerConfig.class).to(DefaultEurekaServerConfig.class).in(Scopes.SINGLETON);
        bind(PeerEurekaNodes.class).in(Scopes.SINGLETON);

        bind(AwsBinderDelegate.class).asEagerSingleton();

        // registry and interfaces
        bind(AwsInstanceRegistry.class).asEagerSingleton();
        bind(InstanceRegistry.class).to(AwsInstanceRegistry.class);
        bind(AbstractInstanceRegistry.class).to(AwsInstanceRegistry.class);
        bind(PeerAwareInstanceRegistry.class).to(AwsInstanceRegistry.class);

        bind(ServerCodecs.class).to(DefaultServerCodecs.class).in(Scopes.SINGLETON);

        bind(EurekaServerContext.class).to(DefaultEurekaServerContext.class).in(Scopes.SINGLETON);
    }

    @Override
    public boolean equals(Object obj) {
        return Ec2EurekaServerModule.class.equals(obj.getClass());
    }

    @Override
    public int hashCode() {
        return Ec2EurekaServerModule.class.hashCode();
    }
}
