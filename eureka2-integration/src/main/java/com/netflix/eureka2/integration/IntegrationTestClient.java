package com.netflix.eureka2.integration;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.EurekaRegistrationClient.RegistrationStatus;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.StdInstanceInfo;
import com.netflix.eureka2.model.interest.Interests;
import com.netflix.eureka2.model.notification.ChangeNotification;
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
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.utils.functions.ChangeNotifications.dataOnlyFilter;

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
    private final Observable<InstanceInfo> registrant;
    private final List<ChangeNotification<InstanceInfo>> expectedLifecycle;

    private final EurekaInterestClient readClient;
    private final EurekaRegistrationClient writeClient;
    private final int unregisterPercentage;
    private final int gapWaitMs;
    private final int endWaitMs;

    public IntegrationTestClient(EurekaInterestClient readClient, EurekaRegistrationClient writeClient) {
        this(APPNAME_PREFIX + UUID.randomUUID().toString(), readClient, writeClient, 15, 300, 10000);
    }

    public IntegrationTestClient(String name, EurekaInterestClient readClient, EurekaRegistrationClient writeClient) {
        this(name, readClient, writeClient, 15, 300, 10000);
    }

    public IntegrationTestClient(String name, EurekaInterestClient readClient, EurekaRegistrationClient writeClient, int unregisterPercentage, int gapWaitMs, int endWaitMs) {
        this.readClient = readClient;
        this.writeClient = writeClient;

        this.unregisterPercentage = unregisterPercentage;
        this.gapWaitMs = gapWaitMs;
        this.endWaitMs = endWaitMs;

        this.appName = name;
        this.registrant = generateLifecycle(appName);
        this.expectedLifecycle = computeExpectedLifecycle(registrant);
    }

    public List<ChangeNotification<InstanceInfo>> getExpectedLifecycle() {
        return expectedLifecycle;
    }

    public List<ChangeNotification<InstanceInfo>> computeExpectedLifecycle(Observable<InstanceInfo> lifecycle) {
        final List<ChangeNotification<InstanceInfo>> expected = new ArrayList<>();
        lifecycle.toBlocking().forEach(new Action1<InstanceInfo>() {
            @Override
            public void call(InstanceInfo instanceInfo) {
                expected.add(new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instanceInfo));
            }
        });

        return expected;
    }

    public List<ChangeNotification<InstanceInfo>> playLifecycle() {
        boolean shouldUnregisterAtEnd = Math.random() * 100 < unregisterPercentage;

        ChangeNotification<InstanceInfo> lastNotification = expectedLifecycle.get(expectedLifecycle.size() - 1);
        final ChangeNotification<InstanceInfo> expectedEnd = shouldUnregisterAtEnd
                ? lastNotification
                : new ChangeNotification<>(ChangeNotification.Kind.Delete, lastNotification.getData());

        final List<ChangeNotification<InstanceInfo>> actualLifecycle = new ArrayList<>();

        final CountDownLatch expectedEndLatch = new CountDownLatch(1);
        Subscription readSubscription = readClient.forInterest(Interests.forApplications(appName))
                .filter(dataOnlyFilter())
                .subscribe(
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

        final CountDownLatch registrantCountLatch = new CountDownLatch(expectedLifecycle.size());
        Observable<RegistrationStatus> registrationRequest = writeClient.register(registrant.doOnEach(new Action1<Notification<? super InstanceInfo>>() {
            @Override
            public void call(Notification<? super InstanceInfo> notification) {
                registrantCountLatch.countDown();
            }
        }));

        Subscription writeSubscription = registrationRequest.subscribe();

        try {
            registrantCountLatch.await(expectedLifecycle.size() * gapWaitMs * 2, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warn("Did not see all expected registration InstanceInfos");
        } finally {
            if (shouldUnregisterAtEnd) {
                writeSubscription.unsubscribe();
            }
        }

        try {
            expectedEndLatch.await(endWaitMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.warn("Did not see expected final notification after waiting");
        }

        readSubscription.unsubscribe();

        return actualLifecycle;
    }

    protected Observable<InstanceInfo> generateLifecycle(final String appName) {
        int size = new Random().nextInt(10) + 5;
        return Observable.range(0, size)
                .map(new Func1<Integer, InstanceInfo>() {
                    @Override
                    public InstanceInfo call(Integer integer) {
                        return new StdInstanceInfo.Builder()
                                .withId(appName)
                                .withApp(appName)
                                .withAsg("asg_" + integer)
                                .build();
                    }
                })
                .zipWith(
                        Observable.interval(gapWaitMs, TimeUnit.MILLISECONDS),
                        new Func2<InstanceInfo, Long, InstanceInfo>() {
                            @Override
                            public InstanceInfo call(InstanceInfo instanceInfo, Long aLong) {
                                return instanceInfo;
                            }
                        }
                );
    }

}
