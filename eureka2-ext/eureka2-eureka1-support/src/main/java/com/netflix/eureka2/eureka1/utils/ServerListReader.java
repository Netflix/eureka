package com.netflix.eureka2.eureka1.utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.functions.InterestFunctions;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Subscription;
import rx.functions.Action1;

import static com.netflix.eureka2.eureka1.utils.Eureka1ModelConverters.toEureka1xInstanceInfos;

/**
 * Helper class for integrating existing Eureka1 clients to Eureka2.
 *
 * @author Tomasz Bak
 */
public class ServerListReader {

    private static final Logger logger = LoggerFactory.getLogger(ServerListReader.class);

    public static final int FIRST_RESOLVE_TIMEOUT_SEC = 30;

    private final EurekaInterestClient interestClient;
    private final Subscription subscription;

    private final AtomicReference<List<com.netflix.appinfo.InstanceInfo>> latestServerList =
            new AtomicReference<List<com.netflix.appinfo.InstanceInfo>>(Collections.<com.netflix.appinfo.InstanceInfo>emptyList());

    private final CountDownLatch firstBatchLatch = new CountDownLatch(1);

    public ServerListReader(ServerResolver serverResolver, final String[] serviceVips, boolean isSecure) {
        this.interestClient = Eurekas.newInterestClientBuilder().withServerResolver(serverResolver).build();
        Interest<InstanceInfo> interest = isSecure ? Interests.forSecureVips(serviceVips) : Interests.forVips(serviceVips);
        this.subscription = interestClient.forInterest(interest)
                .compose(InterestFunctions.buffers())
                .compose(InterestFunctions.snapshots())
                .doOnNext(new Action1<LinkedHashSet<InstanceInfo>>() {
                    @Override
                    public void call(LinkedHashSet<InstanceInfo> instanceInfos) {
                        // Legacy code has little tolerance if we start with empty server list
                        if(!instanceInfos.isEmpty()) {
                            latestServerList.set(toEureka1xInstanceInfos(instanceInfos));
                            firstBatchLatch.countDown();
                        }
                    }
                })
                .doOnError(new Action1<Throwable>() {
                    @Override
                    public void call(Throwable e) {
                        logger.error("Cannot resolve servers for vip addresses " + Arrays.toString(serviceVips), e);
                    }
                })
                .subscribe();
    }

    public List<com.netflix.appinfo.InstanceInfo> getLatestServerList() {
        return latestServerList.get();
    }

    public List<com.netflix.appinfo.InstanceInfo> getLatestServerListOrWait() {
        try {
            firstBatchLatch.await(FIRST_RESOLVE_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // IGNORE
        }
        return latestServerList.get();
    }

    public void shutdown() {
        subscription.unsubscribe();
        interestClient.shutdown();
    }
}
