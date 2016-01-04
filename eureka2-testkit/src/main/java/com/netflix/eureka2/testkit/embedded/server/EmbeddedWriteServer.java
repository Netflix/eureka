package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.inject.Injector;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.EurekaWriteServer;
import rx.functions.Action1;

/**
 * @author Tomasz Bak
 */
@Singleton
public class EmbeddedWriteServer extends EurekaWriteServer {

    @Inject
    public EmbeddedWriteServer(Injector injector) {
        super(injector);
    }

    public ServerResolver getRegistrationResolver() {
        return ServerResolvers.fromHostname("localhost")
                .withPort(injector.getInstance(EurekaWriteServer.class).getServerPort());
    }

    public ServerResolver getInterestResolver() {
        return ServerResolvers.fromHostname("localhost")
                .withPort(injector.getInstance(EurekaWriteServer.class).getServerPort());
    }

    public WriteServerReport serverReport() {
        EurekaWriteServer eurekaWriteServer = injector.getInstance(EurekaWriteServer.class);
        return new WriteServerReport(
                eurekaWriteServer.getServerPort(),
                getEurekaServerRegistry().size(),
                getHttpServerPort(),
                getWebAdminPort()
        );
    }

    public boolean waitForUpStatus(int timeout, TimeUnit timeUnit) {
        final EurekaHealthStatusAggregator healthStatusAggregator = this.injector.getInstance(EurekaHealthStatusAggregator.class);
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        healthStatusAggregator.healthStatus()
                .doOnNext(new Action1<HealthStatusUpdate<EurekaHealthStatusAggregator>>() {
                    @Override
                    public void call(HealthStatusUpdate<EurekaHealthStatusAggregator> healthStatusUpdate) {
                        if (healthStatusUpdate.getStatus() == InstanceInfo.Status.UP) {
                            countDownLatch.countDown();
                        }
                    }
                })
                .subscribe();

        boolean confirmedUp = false;
        try {
            confirmedUp = countDownLatch.await(timeout, timeUnit);
        } catch (Exception e) {
        }

        return confirmedUp;
    }

    public static class WriteServerReport extends AbstractServerReport {
        private final int registrationPort;
        private final int registrySize;

        public WriteServerReport(int serverPort, int registrySize, int httpServerPort, int adminPort) {
            super(httpServerPort, adminPort);
            this.registrationPort = serverPort;
            this.registrySize = registrySize;
        }

        public int getRegistrationPort() {
            return registrationPort;
        }

        public int getRegistrySize() {
            return registrySize;
        }
    }
}
