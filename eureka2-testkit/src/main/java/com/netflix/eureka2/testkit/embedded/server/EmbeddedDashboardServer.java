package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.google.inject.Injector;
import com.netflix.eureka2.EurekaDashboardServer;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EmbeddedDashboardServer extends EurekaDashboardServer {

    @Inject
    public EmbeddedDashboardServer(Injector injector) {
        super(injector);
    }

    public DashboardServerReport serverReport() {
        String dashboardURI = "http://localhost:" + getDashboardPort() + "/dashboard.html";
        return new DashboardServerReport(dashboardURI, getHttpServerPort(), getWebAdminPort());
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
