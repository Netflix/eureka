package com.netflix.eureka.transport.base;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.Message;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.UserContentWithAck;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

/**
 * @author Tomasz Bak
 */
public class BaseMessageBroker implements MessageBroker {

    private final ObservableConnection<Message, Message> connection;
    private final PublishSubject<Void> lifecycleSubject = PublishSubject.create();

    private final Map<String, ReplaySubject<Acknowledgement>> pendingAck = new ConcurrentHashMap<String, ReplaySubject<Acknowledgement>>();
    private final DelayQueue<AckExpiry> expiryQueue = new DelayQueue<AckExpiry>();
    private final ScheduledExecutorService expiryScheduler = Executors.newSingleThreadScheduledExecutor();

    private final Runnable cleanupTask = new Runnable() {
        @Override
        public void run() {
            try {
                while (!expiryQueue.isEmpty()) {
                    String correlationId = expiryQueue.poll().getCorrelationId();
                    ReplaySubject<Acknowledgement> ackSubject = pendingAck.get(correlationId);
                    ackSubject.onError(new TimeoutException("acknowledgement timeout for message with correlation id " + correlationId));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                expiryScheduler.schedule(cleanupTask, 1, TimeUnit.SECONDS);
            }
        }
    };

    public BaseMessageBroker(ObservableConnection<Message, Message> connection) {
        this.connection = connection;
        installAcknowledgementHandler();
    }

    private void installAcknowledgementHandler() {
        connection.getInput().subscribe(new Action1<Message>() {
            @Override
            public void call(Message message) {
                if (!(message instanceof Acknowledgement)) {
                    return;
                }
                Acknowledgement ack = (Acknowledgement) message;
                String correlationId = ack.getCorrelationId();
                ReplaySubject<Acknowledgement> observable = pendingAck.get(correlationId);
                if (observable != null) {
                    observable.onNext(ack);
                    observable.onCompleted();
                }
            }
        });
        expiryScheduler.schedule(cleanupTask, 1, TimeUnit.SECONDS);
    }

    @Override
    public void submit(UserContent message) {
        connection.write(message);
        connection.flush();
    }

    @Override
    public Observable<Acknowledgement> submitWithAck(UserContent message) {
        return submitWithAck(message, 0);
    }

    @Override
    public Observable<Acknowledgement> submitWithAck(UserContent message, long timeout) {
        String correlationId = Long.toString(System.currentTimeMillis());

        ReplaySubject<Acknowledgement> ackObservable = ReplaySubject.create();
        pendingAck.put(correlationId, ackObservable);
        if (timeout > 0) {
            expiryQueue.put(new AckExpiry(correlationId, timeout));
        }

        submit(new UserContentWithAck(message.getContent(), correlationId, timeout));

        return ackObservable;
    }

    @Override
    public boolean acknowledge(UserContentWithAck message) {
        connection.write(new Acknowledgement(message.getCorrelationId()));
        connection.flush();
        return true;
    }

    @Override
    public Observable<Message> incoming() {
        return connection.getInput().filter(new Func1<Message, Boolean>() {
            @Override
            public Boolean call(Message message) {
                return !(message instanceof Acknowledgement);
            }
        }).doOnNext(new Action1<Message>() {
            @Override
            public void call(Message message) {

            }
        });
    }

    @Override
    public void shutdown() {
        Observable<Void> closeObservable = connection.close();
        closeObservable.subscribe(lifecycleSubject);
        expiryScheduler.shutdown();
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return lifecycleSubject;
    }

    private static class AckExpiry implements Delayed {
        private final String correlationId;
        private final long expiry;

        public AckExpiry(String correlationId, long timeout) {
            this.correlationId = correlationId;
            expiry = System.currentTimeMillis() + timeout;
        }

        public String getCorrelationId() {
            return correlationId;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long delay = expiry - System.currentTimeMillis();
            return delay <= 0 ? 0 : unit.convert(delay, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            long d1 = getDelay(TimeUnit.MILLISECONDS);
            long d2 = o.getDelay(TimeUnit.MILLISECONDS);
            if (d1 < d2) {
                return -1;
            }
            if (d1 > d2) {
                return 1;
            }
            return 0;
        }
    }
}
