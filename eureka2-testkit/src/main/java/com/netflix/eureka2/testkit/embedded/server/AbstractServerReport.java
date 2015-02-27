package com.netflix.eureka2.testkit.embedded.server;

/**
* @author Tomasz Bak
*/
public abstract class AbstractServerReport {
    private final String httpServerURI;
    private final String adminBaseURI;

    protected AbstractServerReport(int httpServerPort, int adminPort) {
        this.httpServerURI = "http://localhost:" + httpServerPort;
        this.adminBaseURI = "http://localhost:" + adminPort;
    }

    public String getHttpServerURI() {
        return httpServerURI;
    }

    public String getAdminURI() {
        return adminBaseURI + "/admin";
    }

    public String getHealthCheckURI() {
        return adminBaseURI + "/webadmin/healthcheck";
    }
}
