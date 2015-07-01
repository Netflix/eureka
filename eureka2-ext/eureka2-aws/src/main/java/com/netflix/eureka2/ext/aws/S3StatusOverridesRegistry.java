package com.netflix.eureka2.ext.aws;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.overrides.InstanceStatusOverridesView;
import com.netflix.eureka2.server.service.overrides.InstanceStatusOverridesSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class S3StatusOverridesRegistry implements InstanceStatusOverridesView, InstanceStatusOverridesSource {

    private static final Logger logger = LoggerFactory.getLogger(S3StatusOverridesRegistry.class);

    private final AmazonS3Client amazonS3Client;
    private final AwsConfiguration config;
    private final Scheduler scheduler;

    private final BehaviorSubject<Map<String, Boolean>> overridesSubject;
    private volatile Map<String, Boolean> overridesMap;
    private volatile Subscription refreshSubscription;

    @Inject
    public S3StatusOverridesRegistry(AmazonS3Client amazonS3Client, AwsConfiguration config) {
        this(amazonS3Client, config, Schedulers.io());
    }

    public S3StatusOverridesRegistry(AmazonS3Client amazonS3Client, AwsConfiguration config, Scheduler scheduler) {
        this.amazonS3Client = amazonS3Client;
        this.config = config;
        this.scheduler = scheduler;

        this.overridesMap = new ConcurrentHashMap<>();
        this.overridesSubject = BehaviorSubject.create();
        overridesSubject.onNext(overridesMap);
    }

    @PostConstruct
    public void start() {
        refreshSubscription = Observable.timer(config.getRefreshIntervalSec()/2, config.getRefreshIntervalSec(), TimeUnit.SECONDS, scheduler)
                .doOnNext(new Action1<Long>() {
                    @Override
                    public void call(Long aLong) {
                        Map<String, Boolean> snapshot = readS3();
                        if (!snapshot.isEmpty()) {
                            overridesMap = snapshot;
                            overridesSubject.onNext(overridesMap);
                        }
                    }
                })
                .subscribe(new Subscriber<Long>() {
                    @Override
                    public void onCompleted() {
                        logger.info("S3 refresh for S3InstanceStatusOverrides onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        logger.error("S3 refresh for S3InstanceStatusOverrides error", e);
                    }

                    @Override
                    public void onNext(Long aLong) {

                    }
                });
    }

    @PreDestroy
    public void stop() {
        if (refreshSubscription != null) {
            refreshSubscription.unsubscribe();
        }

        overridesSubject.onCompleted();
        overridesMap.clear();
    }

    @Override
    public Observable<Boolean> shouldApplyOutOfService(final InstanceInfo instanceInfo) {
        return overridesSubject
                .map(new Func1<Map<String, Boolean>, Boolean>() {
                    @Override
                    public Boolean call(Map<String, Boolean> map) {
                        return map.containsKey(instanceInfo.getId());
                    }
                })
                .distinctUntilChanged()
                .cache(1);
    }

    @Override
    public Observable<Boolean> setOutOfService(final InstanceInfo instanceInfo) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                InputStream inputStream = new ByteArrayInputStream(new byte[0]);
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(0);

                String name = toS3Name(config.getPrefix(), instanceInfo.getId());

                try {
                    PutObjectResult result = amazonS3Client.putObject(
                            config.getBucketName(),
                            name,
                            inputStream,
                            metadata
                    );

                    logger.debug("S3 put result: {}", result);
                    subscriber.onNext(true);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    logger.error("failed applying OOS for instance {}", instanceInfo, e);
                    subscriber.onNext(false);
                    subscriber.onError(e);
                }
            }
        }).cache(1);
    }

    @Override
    public Observable<Boolean> unsetOutOfService(final InstanceInfo instanceInfo) {
        return Observable.create(new Observable.OnSubscribe<Boolean>() {
            @Override
            public void call(Subscriber<? super Boolean> subscriber) {
                try {
                    amazonS3Client.deleteObject(config.getBucketName(), toS3Name(config.getPrefix(), instanceInfo.getId()));

                    subscriber.onNext(true);
                    subscriber.onCompleted();
                } catch (Exception e) {
                    logger.error("failed remove OOS for instance {}", instanceInfo, e);
                    subscriber.onNext(false);
                    subscriber.onError(e);
                }
            }
        }).cache(1);
    }

    /* visible for testing */ String toS3Name(String name, String id) {
        return name + "/" + id;
    }

    /* visible for testing */ String fromS3Name(String s3Name) {
        String[] parts = s3Name.split("/");
        if (parts.length == 2) {
            return parts[1];
        }
        return null;
    }

    // TODO: add metrics on timing for this
    public Map<String, Boolean> readS3() {
        try {
            Map<String, Boolean> result = new HashMap<>();

            ListObjectsRequest request = new ListObjectsRequest()
                    .withBucketName(config.getBucketName())
                    .withPrefix(config.getPrefix());

            ObjectListing listing;
            do {
                listing = amazonS3Client.listObjects(request);
                for (S3ObjectSummary summary : listing.getObjectSummaries()) {
                    String id = fromS3Name(summary.getKey());
                    if (id != null) {
                        result.put(id, true);
                    }
                }
                request.setMarker(listing.getNextMarker());
            } while (listing.isTruncated());

            return result;
        } catch (Exception e) {
            logger.error("Failed reading data from s3", e);
        }

        return Collections.EMPTY_MAP;
    }
}
