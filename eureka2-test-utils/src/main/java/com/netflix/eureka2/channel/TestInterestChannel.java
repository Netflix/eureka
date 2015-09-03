package com.netflix.eureka2.channel;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import rx.Observable;

/**
 * @author David Liu
 */
public class TestInterestChannel extends TestChannel<InterestChannel, Interest<InstanceInfo>> implements InterestChannel {
    public TestInterestChannel(InterestChannel delegate, Integer id) {
        super(delegate, id);
    }

    @Override
    public Observable<Void> change(Interest<InstanceInfo> newInterest) {
        operations.add(newInterest);
        return delegate.change(newInterest);
    }

    @Override
    public Source getSource() {
        return delegate.getSource();
    }

    @Override
    public Observable<ChangeNotification<InstanceInfo>> getChangeNotificationStream() {
        return delegate.getChangeNotificationStream();
    }
}
