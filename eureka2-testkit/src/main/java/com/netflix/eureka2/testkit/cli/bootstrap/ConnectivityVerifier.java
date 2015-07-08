package com.netflix.eureka2.testkit.cli.bootstrap;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.ClientBuilder;
import io.reactivex.netty.client.ClientMetricsEvent;
import io.reactivex.netty.client.ClientMetricsEvent.EventType;
import io.reactivex.netty.client.RxClient;
import io.reactivex.netty.metrics.MetricEventsListener;
import rx.Notification;
import rx.Observable;
import rx.Subscription;
import rx.functions.Func1;

/**
 * This class tests connectivity to Eureka endpoint. Currently limited to check
 * if a port is open, but in the future when all protocols implement handshake will
 * also detect protocol version.
 *
 * @author Tomasz Bak
 */
class ConnectivityVerifier {

    private final String hostName;
    private final int port;

    ConnectivityVerifier(String hostName, int port) {
        this.hostName = hostName;
        this.port = port;
    }

    boolean isOpen(long timeout, TimeUnit timeUnit) throws InterruptedException {
        Notification<Boolean> result = Observable.timer(0, TimeUnit.SECONDS).flatMap(new Func1<Long, Observable<Boolean>>() {
            @Override
            public Observable<Boolean> call(Long tick) {
                try {
                    return Observable.just(checkIfOpen());
                } catch (Exception e) {
                    return Observable.error(e);
                }
            }
        }).timeout(timeout, timeUnit).materialize().toBlocking().first();

        return result.isOnNext() && result.getValue();
    }

    private boolean checkIfOpen() {
        final LinkedBlockingDeque<ClientMetricsEvent<EventType>> events = new LinkedBlockingDeque<>();
        ClientBuilder<Object, Object> clientBuilder = RxNetty.newTcpClientBuilder(hostName, port);
        clientBuilder.getEventsSubject().subscribe(new MetricEventsListener<ClientMetricsEvent<EventType>>() {
            @Override
            public void onEvent(ClientMetricsEvent<EventType> event, long duration, TimeUnit timeUnit, Throwable throwable, Object value) {
                if (event.getType() == EventType.ConnectSuccess || event.getType() == EventType.ConnectFailed) {
                    events.add(event);
                }
            }

            @Override
            public void onCompleted() {
            }

            @Override
            public void onSubscribe() {
            }
        });
        RxClient<Object, Object> tcpClient = clientBuilder.build();
        Subscription clientSubscription = null;
        try {
            clientSubscription = tcpClient.connect().subscribe();
            EventType event = events.poll(5, TimeUnit.SECONDS).getType();
            return event == EventType.ConnectSuccess;
        } catch (InterruptedException ignored) {
            return false;
        } finally {
            if (clientSubscription != null) {
                clientSubscription.unsubscribe();
            }
            tcpClient.shutdown();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ConnectivityVerifier verifier = new ConnectivityVerifier("ec2-50-19-255-84.compute-1.amazonaws.com", 12103);
        System.out.println(verifier.isOpen(5, TimeUnit.SECONDS));
    }
}
