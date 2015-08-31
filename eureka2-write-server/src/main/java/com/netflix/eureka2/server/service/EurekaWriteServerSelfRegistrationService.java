package com.netflix.eureka2.server.service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.utils.rx.LoggingSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

/**
 * @author David Liu
 */
@Singleton
public class EurekaWriteServerSelfRegistrationService extends SelfRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServerSelfRegistrationService.class);

    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor;
    private final Subscriber<Void> localSubscriber;

    private volatile Observable<ChangeNotification<InstanceInfo>> data;
    private volatile Observable<Void> control;
    private volatile Source selfSource;

    @Inject
    public EurekaWriteServerSelfRegistrationService(SelfInfoResolver resolver, EurekaRegistrationProcessor registrationProcessor) {
        super(resolver);
        this.registrationProcessor = registrationProcessor;
        this.localSubscriber = new LoggingSubscriber<>(logger);
    }

    @PostConstruct
    @Override
    public void init() {
        super.init();
    }

    @Override
    public Observable<Void> connect(final Observable<InstanceInfo> registrant) {
        final Observable<InstanceInfo> input = registrant.replay(1).refCount();
        final ReplaySubject<InstanceInfo> lastInstanceInfoSubject = ReplaySubject.createWithSize(1);

        data = input
                .materialize()
                .concatMap(new Func1<Notification<InstanceInfo>, Observable<? extends ChangeNotification<InstanceInfo>>>() {
                    @Override
                    public Observable<? extends ChangeNotification<InstanceInfo>> call(Notification<InstanceInfo> rxNotification) {
                        switch (rxNotification.getKind()) {
                            case OnNext:
                                ChangeNotification<InstanceInfo> notification = new ChangeNotification<>(ChangeNotification.Kind.Add, rxNotification.getValue());
                                return Observable.just(notification);
                            case OnError:
                            case OnCompleted:
                            default:
                                return lastInstanceInfoSubject.take(1).flatMap(new Func1<InstanceInfo, Observable<ChangeNotification<InstanceInfo>>>() {
                                    @Override
                                    public Observable<ChangeNotification<InstanceInfo>> call(InstanceInfo instanceInfo) {
                                        logger.info("unregistering self InstanceInfo {}", instanceInfo);
                                        return Observable.just(new ChangeNotification<>(ChangeNotification.Kind.Delete, instanceInfo));
                                    }
                                });
                        }
                    }
                });

        control = input
                .take(1)
                .doOnNext(new Action1<InstanceInfo>() {
                    @Override
                    public void call(InstanceInfo instanceInfo) {
                        selfSource = new Source(Source.Origin.LOCAL, instanceInfo.getId());
                        logger.info("registering self InstanceInfo {}", instanceInfo);
                        registrationProcessor.connect(instanceInfo.getId(), selfSource, data).subscribe(localSubscriber);
                        input.subscribe(lastInstanceInfoSubject);
                    }
                })
                .ignoreElements()
                .cast(Void.class);

        return Observable.<Void>empty()
                .doOnSubscribe(new Action0() {
                    @Override
                    public void call() {
                        control.subscribe(localSubscriber);
                    }
                })
                .share();
    }

    @Override
    public void cleanUpResources() {
        if (!localSubscriber.isUnsubscribed()) {
            localSubscriber.unsubscribe();
        }
    }
}
