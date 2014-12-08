package com.netflix.eureka2.server.bridge;

import com.netflix.eureka2.registry.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscriber;

/**
 * @author David Liu
 */
public class OperatorInstanceInfoFromV1 implements Observable.Operator<InstanceInfo, com.netflix.appinfo.InstanceInfo> {
    private static final Logger logger = LoggerFactory.getLogger(OperatorInstanceInfoFromV1.class);

    private final InstanceInfoConverter converter;

    public OperatorInstanceInfoFromV1(InstanceInfoConverter converter) {
        this.converter = converter;
    }

    @Override
    public Subscriber<? super com.netflix.appinfo.InstanceInfo> call(final Subscriber<? super InstanceInfo> subscriber) {
        return new Subscriber<com.netflix.appinfo.InstanceInfo>() {
            @Override
            public void onCompleted() {
                subscriber.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                subscriber.onError(e);
            }

            @Override
            public void onNext(com.netflix.appinfo.InstanceInfo v1InstanceInfo) {
                try {
                    InstanceInfo instanceInfo = converter.fromV1(v1InstanceInfo);
                    subscriber.onNext(instanceInfo);
                } catch (Exception e) {  // swallow this for now
                    logger.error("Error converting instanceInfo", e);
                    e.printStackTrace();
                }
            }
        };
    }
}
