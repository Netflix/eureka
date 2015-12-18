package com.netflix.eureka2.testkit.embedded.server;

import javax.inject.Inject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.google.inject.Injector;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.EurekaReadServer;
import com.netflix.eureka2.server.EurekaWriteServer;
import rx.functions.Action1;

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
