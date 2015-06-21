package com.netflix.eureka2.testkit.embedded.server;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.AbstractEurekaServer;
import com.netflix.eureka2.server.config.EurekaCommonConfig;
import com.netflix.eureka2.server.http.EurekaHttpServer;
import com.netflix.eureka2.server.module.CommonEurekaServerModule;
import com.netflix.eureka2.server.module.EurekaExtensionModule;
import com.netflix.eureka2.server.spi.ExtAbstractModule.ServerType;
import netflix.adminresources.AdminResourcesContainer;
import netflix.adminresources.resources.Eureka2InterestClientProviderImpl;

/**
 * @author Tomasz Bak
 */
public abstract class EmbeddedEurekaServer<C extends EurekaCommonConfig, R> extends AbstractEurekaServer<C> {
    private final boolean withExt;
    private final boolean withAdminUI;
    private final ServerType serverType;
    protected final C config;

    protected EmbeddedEurekaServer(ServerType serverType, C config, boolean withExt, boolean withAdminUI) {
        super(config);
        this.serverType = serverType;
        this.config = config;
        this.withExt = withExt;
        this.withAdminUI = withAdminUI;
    }

    public void shutdown() {
        injector.shutdown();
    }

    public Injector getInjector() {
        return injector;
    }

    public SourcedEurekaRegistry<InstanceInfo> getEurekaServerRegistry() {
        return injector.getInstance(SourcedEurekaRegistry.class);
    }

    public int getWebAdminPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector == null ? -1 : injector.getInstance(AdminResourcesContainer.class).getServerPort();
    }

    public int getHttpServerPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(EurekaHttpServer.class).serverPort();
    }

    protected abstract ServerResolver getInterestResolver();

    public abstract R serverReport();

    @Override
    protected Module getModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                install(new CommonEurekaServerModule());
                install(new EurekaExtensionModule(serverType));
                if (withAdminUI) {
                    install(createAdminUIModule());
                }
            }
        };
    }

    protected EmbeddedKaryonAdminModule createAdminUIModule() {
        return new EmbeddedKaryonAdminModule() {

            @Override
            protected int getEurekaWebAdminPort() {
                return config.getWebAdminPort();
            }

            @Override
            protected int getEurekaHttpServerPort() {
                return getHttpServerPort();
            }

            @Override
            protected ServerResolver getInterestResolver() {
                return EmbeddedEurekaServer.this.getInterestResolver();
            }
        };
    }

    protected void loadInstanceProperties(Properties props) {
        // TODO Until admin WEB configuration is more flexible we take port of first write server
        String writeServer = config.getServerList()[0];
        Matcher matcher = Pattern.compile("[^:]+:\\d+:(\\d+):\\d+").matcher(writeServer);
        if (matcher.matches()) {
            String interestPort = matcher.group(1);
            props.setProperty(Eureka2InterestClientProviderImpl.CONFIG_DISCOVERY_PORT, interestPort);
        }
    }
}
