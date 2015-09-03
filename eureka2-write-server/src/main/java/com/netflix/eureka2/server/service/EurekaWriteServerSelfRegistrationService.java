package com.netflix.eureka2.server.service;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.server.registry.EurekaRegistrationProcessor;
import com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver;
import com.netflix.eureka2.utils.rx.SettableSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Notification;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

/**
 * @author David Liu
 */
@Singleton
public class EurekaWriteServerSelfRegistrationService extends SelfRegistrationService {

    private static final Logger logger = LoggerFactory.getLogger(EurekaWriteServerSelfRegistrationService.class);

    private final EurekaRegistrationProcessor<InstanceInfo> registrationProcessor;
    private final SettableSubscriber<InstanceInfo> settableSubscriber;

    private volatile Observable<ChangeNotification<InstanceInfo>> data;
    private volatile Source selfSource;

    @Inject
    public EurekaWriteServerSelfRegistrationService(SelfInfoResolver resolver, EurekaRegistrationProcessor registrationProcessor) {
        super(resolver);
        this.registrationProcessor = registrationProcessor;
        this.settableSubscriber = new SettableSubscriber<>();
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
                .lift(new Observable.Operator<InstanceInfo, InstanceInfo>() {
                    @Override
                    public Subscriber<? super InstanceInfo> call(Subscriber<? super InstanceInfo> subscriber) {
                        settableSubscriber.setWrapped(subscriber);
                        return settableSubscriber;
                    }
                })
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

        return input
                .take(1)
                .switchMap(new Func1<InstanceInfo, Observable<? extends Void>>() {
                    @Override
                    public Observable<? extends Void> call(InstanceInfo instanceInfo) {
                        selfSource = new Source(Source.Origin.LOCAL, instanceInfo.getId());
                        logger.info("registering self InstanceInfo {}", instanceInfo);
                        input.subscribe(lastInstanceInfoSubject);
                        return registrationProcessor.connect(instanceInfo.getId(), selfSource, data);
                    }
                });
    }

    @Override
    public void cleanUpResources() {
        settableSubscriber.setOnComplete();
    }
}
