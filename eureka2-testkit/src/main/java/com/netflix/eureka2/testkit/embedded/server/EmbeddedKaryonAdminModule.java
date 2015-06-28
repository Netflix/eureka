package com.netflix.eureka2.testkit.embedded.server;

import com.google.inject.AbstractModule;
import com.netflix.eureka2.server.health.KaryonHealthCheckHandler;
import netflix.admin.AdminConfigImpl;
import netflix.admin.AdminContainerConfig;
import netflix.adminresources.AdminPageRegistry;
import netflix.adminresources.AdminResourcesContainer;
import netflix.adminresources.pages.EnvPage;
import netflix.adminresources.pages.Eureka2Page;
import netflix.adminresources.pages.Eureka2StatusPage;
import netflix.karyon.health.HealthCheckHandler;
import netflix.karyon.health.HealthCheckInvocationStrategy;
import netflix.karyon.health.SyncHealthCheckInvocationStrategy;

/**
 * This class encapsulates Karyon admin UI construction in an embedded environment.
 * In embedded mode services run a different than default or ephemeral ports. If the port is ephemeral
 * its value is established only after server is started. As Karyon admin is driven by Archaius configuration
 * which is singleton in nature running parallel Karyon admin UIs requires extra coordination effort.
 *
 * @author Tomasz Bak
 */
public class EmbeddedKaryonAdminModule extends AbstractModule {

    private final int webAdminPort;

    public EmbeddedKaryonAdminModule(int webAdminPort) {
        this.webAdminPort = webAdminPort;
    }

    @Override
    protected void configure() {
        AdminPageRegistry adminRegistry = new AdminPageRegistry();
        adminRegistry.add(new EnvPage());
        adminRegistry.add(new Eureka2Page());
        adminRegistry.add(new Eureka2StatusPage());

        bind(AdminContainerConfig.class).toInstance(new EmbeddedAdminContainerConfig(webAdminPort));
        bind(AdminPageRegistry.class).toInstance(adminRegistry);
        bind(AdminResourcesContainer.class).asEagerSingleton();

        bind(HealthCheckHandler.class).to(KaryonHealthCheckHandler.class).asEagerSingleton();
        bind(HealthCheckInvocationStrategy.class).to(SyncHealthCheckInvocationStrategy.class).asEagerSingleton();
    }

    public static class EmbeddedAdminContainerConfig implements AdminContainerConfig {

        private final int port;

        public EmbeddedAdminContainerConfig(int port) {
            this.port = port;
        }

        @Override
        public boolean shouldIsolateResources() {
            return AdminConfigImpl.ISOLATE_RESOURCES_DEFAULT;
        }

        @Override
        public boolean shouldEnable() {
            return AdminConfigImpl.SERVER_ENABLE_DEFAULT;
        }

        @Override
        public String templateResourceContext() {
            return AdminConfigImpl.TEMPLATE_CONTEXT_DEFAULT;
        }

        @Override
        public String ajaxDataResourceContext() {
            return AdminConfigImpl.RESOURCE_CONTEXT_DEFAULT;
        }

        @Override
        public String jerseyResourcePkgList() {
            return AdminConfigImpl.JERSEY_CORE_RESOURCES_DEFAULT;
        }

        @Override
        public String jerseyViewableResourcePkgList() {
            return AdminConfigImpl.JERSEY_VIEWABLE_RESOURCES_DEFAULT;
        }

        @Override
        public boolean shouldScanClassPathForPluginDiscovery() {
            return false;
        }

        @Override
        public int listenPort() {
            return port;
        }
    }
}
