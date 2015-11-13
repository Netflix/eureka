package com.netflix.eureka2.performance.interest;

import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.spi.protocol.interest.InterestRegistration;
import com.netflix.eureka2.spi.transport.EurekaConnection;
import rx.Observable;
import rx.subjects.PublishSubject;

/**
 * @author Tomasz Bak
 */
public class MockedMessageConnection implements EurekaConnection {

    private final PublishSubject<Object> incomingSubject = PublishSubject.create();
    private final PerformanceScoreBoard scoreBoard;

    public MockedMessageConnection(PerformanceScoreBoard scoreBoard) {
        this.scoreBoard = scoreBoard;
    }

    public void subscribeTo(Interest<InstanceInfo> interest) {
        incomingSubject.onNext(ProtocolModel.getDefaultModel().newInterestRegistration(interest));
    }

    @Override
    public String name() {
        return "perf";
    }

    @Override
    public Observable<Void> submit(Object message) {
        return Observable.empty();
    }

    @Override
    public Observable<Void> submitWithAck(Object message) {
        scoreBoard.processedNotificationsIncrement();
        return Observable.empty();
    }

    @Override
    public Observable<Void> submitWithAck(Object message, long timeout) {
        return Observable.empty();
    }

    @Override
    public Observable<Void> acknowledge() {
        return Observable.empty();
    }

    @Override
    public Observable<Object> incoming() {
        return incomingSubject;
    }

    @Override
    public Observable<Void> onError(Throwable error) {
        return Observable.error(error);
    }

    @Override
    public Observable<Void> onCompleted() {
        return Observable.empty();
    }

    @Override
    public void shutdown() {
    }

    @Override
    public void shutdown(Throwable e) {
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return Observable.never();
    }
}
