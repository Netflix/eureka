package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;

import com.google.inject.Injector;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.server.EurekaReadServer;
import com.netflix.eureka2.server.EurekaWriteServer;

/**
 * @author Tomasz Bak
 */
public class EmbeddedReadServer extends EurekaReadServer {

    @Inject
    public EmbeddedReadServer(Injector injector) {
        super(injector);
    }

    public ServerResolver getInterestResolver() {
        return ServerResolvers.fromHostname("localhost")
                .withPort(injector.getInstance(EurekaWriteServer.class).getInterestPort());
    }

    public ReadServerReport serverReport() {
        return new ReadServerReport(getInterestPort(), getHttpServerPort(), getWebAdminPort());
    }

    public static class ReadServerReport extends AbstractServerReport {
        private final int interestPort;

        public ReadServerReport(int interestPort, int httpServerPort, int adminPort) {
            super(httpServerPort, adminPort);
            this.interestPort = interestPort;
        }

        public int getInterestPort() {
            return interestPort;
        }
    }
}
