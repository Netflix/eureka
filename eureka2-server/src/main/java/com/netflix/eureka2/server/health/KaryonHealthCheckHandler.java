package com.netflix.eureka2.server.health;

import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.health.EurekaHealthStatusAggregator;
import com.netflix.eureka2.health.HealthStatusUpdate;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import io.netty.handler.codec.http.HttpResponseStatus;
import netflix.karyon.health.HealthCheckHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscriber;
import rx.Subscription;

/**
 * @author Tomasz Bak
 */
public class KaryonHealthCheckHandler implements HealthCheckHandler {

    private static final Logger logger = LoggerFactory.getLogger(KaryonHealthCheckHandler.class);

    private final Subscription subscription;
    private final AtomicReference<HttpResponseStatus> status = new AtomicReference<>(HttpResponseStatus.SERVICE_UNAVAILABLE);

    @Inject
    public KaryonHealthCheckHandler(EurekaHealthStatusAggregatorImpl healthStatusAggregator) {
        subscription = healthStatusAggregator.healthStatus().subscribe(new Subscriber<HealthStatusUpdate<EurekaHealthStatusAggregator>>() {
            @Override
            public void onCompleted() {
                logger.warn("KaryonHealthCheckHandler terminated; health check status will be no longer updated");
            }

            @Override
            public void onError(Throwable e) {
                logger.error("KaryonHealthCheckHandler terminated with an error; health check status will be no longer updated", e);
            }

            @Override
            public void onNext(HealthStatusUpdate<EurekaHealthStatusAggregator> update) {
                status.set(update.getStatus() == Status.UP ? HttpResponseStatus.OK : HttpResponseStatus.SERVICE_UNAVAILABLE);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        status.set(HttpResponseStatus.SERVICE_UNAVAILABLE);
        subscription.unsubscribe();
    }

    @Override
    public int getStatus() {
        return status.get().code();
    }
}
