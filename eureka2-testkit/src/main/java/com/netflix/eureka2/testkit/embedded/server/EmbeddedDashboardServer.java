package com.netflix.eureka2.testkit.embedded.server;

import java.util.Properties;

import com.google.inject.Module;
import com.netflix.eureka2.DashboardHttpServer;
import com.netflix.eureka2.EurekaDashboardModule;
import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaInterestClientBuilder;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClientBuilder;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.config.EurekaDashboardConfig;
import com.netflix.eureka2.registry.datacenter.LocalDataCenterInfo.DataCenterType;
import com.netflix.eureka2.testkit.embedded.server.EmbeddedDashboardServer.DashboardServerReport;
import com.netflix.eureka2.transport.EurekaTransports.Codec;

/**
 * @author Tomasz Bak
 */
public class EmbeddedDashboardServer extends EmbeddedEurekaServer<EurekaDashboardConfig, DashboardServerReport> {

    private static final String DASHBOARD_SERVER_NAME = "eureka2-dashboard";
    private static final int DASHBOARD_SERVER_PORTS_FROM = 16000;

    private final int discoveryPort;
    private final ServerResolver registrationServerResolver;
    private final ServerResolver interestServerResolver;

    public EmbeddedDashboardServer(EurekaDashboardConfig config,
                                   int discoveryPort, // TODO: remove this property once eureka2 UI tab is refactored
                                   ServerResolver registrationServerResolver,
                                   ServerResolver interestServerResolver,
                                   boolean withExt,
                                   boolean withDashboard) {
        super(config, withExt, withDashboard);
        this.discoveryPort = discoveryPort;
        this.registrationServerResolver = registrationServerResolver;
        this.interestServerResolver = interestServerResolver;
    }

    @Override
    public void start() {
        final EurekaRegistrationClient registrationClient = new EurekaRegistrationClientBuilder()
                .withTransportConfig(config)
                .withRegistryConfig(config)
                .fromWriteServerResolver(registrationServerResolver)
                .build();

        final EurekaInterestClient interestClient = new EurekaInterestClientBuilder()
                .withTransportConfig(config)
                .withRegistryConfig(config)
                .fromReadServerResolver(interestServerResolver)
                .build();

        Module[] modules = {new EurekaDashboardModule(config, registrationClient, interestClient)};
        setup(modules);
    }

    @Override
    protected void loadInstanceProperties(Properties props) {
        super.loadInstanceProperties(props);
        props.setProperty("eureka.client.discovery-endpoint.port", Integer.toString(discoveryPort));
    }

    @Override
    protected ServerResolver getInterestResolver() {
        return interestServerResolver;
    }

    public int getDashboardPort() {
        // Since server might be started on the ephemeral port, we need to get it directly from RxNetty server
        return injector.getInstance(DashboardHttpServer.class).serverPort();
    }

    @Override
    public DashboardServerReport serverReport() {
        String dashboardURI = "http://localhost:" + getDashboardPort() + "/dashboard.html";
        return new DashboardServerReport(dashboardURI, getHttpServerPort(), getWebAdminPort());
    }

    public static EmbeddedDashboardServer newDashboard(ServerResolver registrationServerResolver,
                                                       ServerResolver discoveryServerResolver,
                                                       int discoveryPort,
                                                       boolean withExt,
                                                       boolean withAdminUI,
                                                       boolean ephemeralPorts,
                                                       Codec codec) {

        int dashboardPort = ephemeralPorts ? 0 : DASHBOARD_SERVER_PORTS_FROM;
        int webAdminPort = ephemeralPorts ? 0 : DASHBOARD_SERVER_PORTS_FROM + 1;

        EurekaDashboardConfig config = EurekaDashboardConfig.newBuilder()
                .withAppName(DASHBOARD_SERVER_NAME)
                .withVipAddress(DASHBOARD_SERVER_NAME)
                .withDataCenterType(DataCenterType.Basic)
                .withCodec(codec)
                .withShutDownPort(-1) // No shutdown service in embedded mode
                .withWebAdminPort(webAdminPort)
                .withDashboardPort(dashboardPort)
                .build();
        return new EmbeddedDashboardServer(config, discoveryPort, registrationServerResolver, discoveryServerResolver, withExt, withAdminUI);
    }

    public static class DashboardServerReport extends AbstractServerReport {

        private final String dashboardURI;

        public DashboardServerReport(String dashboardURI, int httpServerPort, int webAdminPort) {
            super(httpServerPort, webAdminPort);
            this.dashboardURI = dashboardURI;
        }

        public String getDashboardURI() {
            return dashboardURI;
        }
    }
}
