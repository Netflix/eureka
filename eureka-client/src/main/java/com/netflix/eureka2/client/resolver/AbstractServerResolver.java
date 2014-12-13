package com.netflix.eureka2.client.resolver;

import com.netflix.eureka2.interests.ChangeNotification;
import netflix.ocelli.LoadBalancer;
import netflix.ocelli.LoadBalancerBuilder;
import netflix.ocelli.MembershipEvent;
import netflix.ocelli.MembershipEvent.EventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Observable.Operator;
import rx.Subscriber;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractServerResolver implements ServerResolver {

    private static final Logger logger = LoggerFactory.getLogger(AbstractServerResolver.class);

    private static final Operator<MembershipEvent<Server>, ChangeNotification<Server>> OCELLI_CONVERTER = new Operator<MembershipEvent<Server>, ChangeNotification<Server>>() {
        @Override
        public Subscriber<ChangeNotification<Server>> call(final Subscriber<? super MembershipEvent<Server>> subscriber) {
            return new Subscriber<ChangeNotification<Server>>() {
                @Override
                public void onCompleted() {
                    subscriber.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    subscriber.onError(e);
                }

                @Override
                public void onNext(ChangeNotification<Server> notification) {
                    switch (notification.getKind()) {
                        case Add:
                            subscriber.onNext(new MembershipEvent<Server>(EventType.ADD, notification.getData()));
                            break;
                        case Modify:
                            // This should never happen
                            logger.warn("unexpected modify change notification; it is not supported by ocelli load balancer");
                            break;
                        case Delete:
                            subscriber.onNext(new MembershipEvent<Server>(EventType.REMOVE, notification.getData()));
                    }
                }
            };
        }
    };

    private final LoadBalancerBuilder<Server> loadBalancerBuilder;
    private LoadBalancer<Server> loadBalancer;

    protected AbstractServerResolver(LoadBalancerBuilder<Server> loadBalancerBuilder) {
        this.loadBalancerBuilder = loadBalancerBuilder;
    }

    @Override
    public Observable<Server> resolve() {
        if (loadBalancer == null) {
            loadBalancer = loadBalancerBuilder.withMembershipSource(serverUpdates().lift(OCELLI_CONVERTER)).build();
        }
        return loadBalancer.choose();
    }

    @Override
    public void close() {
        if (loadBalancer != null) {
            loadBalancer.shutdown();
            loadBalancer = null;
        }
    }

    protected abstract Observable<ChangeNotification<Server>> serverUpdates();
}
