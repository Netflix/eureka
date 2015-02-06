package com.netflix.eureka2.integration;

import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.functions.Func2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.interests.ChangeNotifications.batchMarkerFilter;

/**
 * A test client that generates a random sequence of register/update/unregister events,
 * and subscribes to itself.
 *
 * @author David Liu
 */
public class IntegrationTestClient {

    private static final Logger logger = LoggerFactory.getLogger(IntegrationTestClient.class);

    private static final String APPNAME_PREFIX = "IntegTestClient_";

    private final String appName;
    private final List<ChangeNotification<InstanceInfo>> lifecycle;
    private final EurekaClient readClient;
    private final EurekaClient writeClient;
    private final int unregisterPercentage;
    private final int gapWaitMs;
    private final int endWaitMs;

    private final List<ChangeNotification<InstanceInfo>> actualLifecycle;

    public IntegrationTestClient(EurekaClient readClient, EurekaClient writeClient) {
        this(readClient, writeClient, 15, 300, 10000);
    }

    public IntegrationTestClient(EurekaClient readClient, EurekaClient writeClient, int unregisterPercentage, int gapWaitMs, int endWaitMs) {
        this.readClient = readClient;
        this.writeClient = writeClient;

        this.unregisterPercentage = unregisterPercentage;
        this.gapWaitMs = gapWaitMs;
        this.endWaitMs = endWaitMs;

        this.appName = APPNAME_PREFIX + UUID.randomUUID().toString();
        this.lifecycle = generateLifecycle(appName);
        this.actualLifecycle = new ArrayList<>();
    }

    public List<ChangeNotification<InstanceInfo>> getExpectedLifecycle() {
        return lifecycle;
    }

    public List<ChangeNotification<InstanceInfo>> playLifecycle() {
        final ChangeNotification<InstanceInfo> expectedEnd = lifecycle.get(lifecycle.size() - 1);

        final CountDownLatch expectedEndLatch = new CountDownLatch(1);
        Subscription subscription = readClient.forApplication(appName).filter(batchMarkerFilter()).subscribe(
                new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        actualLifecycle.add(notification);
                        if (expectedEnd.getData().equals(notification.getData())) {
                            expectedEndLatch.countDown();
                        }
                    }
                }
        );

        final CountDownLatch registerLatch = new CountDownLatch(lifecycle.size());
        Observable.from(lifecycle)
                .zipWith(
                        Observable.interval(gapWaitMs, TimeUnit.MILLISECONDS),
                        new Func2<ChangeNotification<InstanceInfo>, Long, ChangeNotification<InstanceInfo>>() {
                            @Override
                            public ChangeNotification<InstanceInfo> call(ChangeNotification<InstanceInfo> notification, Long aLong) {
                                return notification;
                            }
                        })
                .flatMap(new Func1<ChangeNotification<InstanceInfo>, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(ChangeNotification<InstanceInfo> notification) {
                        if (notification.getKind() == ChangeNotification.Kind.Delete) {
                            return writeClient.unregister(notification.getData()).retry(3);
                        } else {
                            return writeClient.register(notification.getData()).retry(3);
                        }
                    }
                })
                .doOnEach(new Action1<Notification<? super Void>>() {
                    @Override
                    public void call(Notification<? super Void> notification) {
                        registerLatch.countDown();
                    }
                })
                .subscribe();

        try {
            registerLatch.await(lifecycle.size() * gapWaitMs * 2, TimeUnit.MILLISECONDS);

            if (!expectedEndLatch.await(endWaitMs, TimeUnit.MILLISECONDS)) {
                logger.warn("Did not see expected end after 10 seconds");
            }
        } catch (Exception e) {}

        subscription.unsubscribe();
        return Collections.unmodifiableList(actualLifecycle);
    }

    protected List<ChangeNotification<InstanceInfo>> generateLifecycle(String appName) {
        int size = new Random().nextInt(10) + 5;
        List<ChangeNotification<InstanceInfo>> lifecycle = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            lifecycle.add(randomNotification(randomInstanceInfo(appName)));
        }

        return lifecycle;
    }

    protected InstanceInfo randomInstanceInfo(String appName) {
        return new InstanceInfo.Builder()
                .withId(appName)
                .withApp(appName)
                .withAsg("asg_" + new Random().nextInt(100))
                .build();
    }

    protected ChangeNotification<InstanceInfo> randomNotification(InstanceInfo instanceInfo) {
        if (Math.random() * 100 < unregisterPercentage) {
            return new ChangeNotification<>(ChangeNotification.Kind.Delete, instanceInfo);
        } else {
            return new ChangeNotification<>(ChangeNotification.Kind.Add, instanceInfo);
        }
    }

}
