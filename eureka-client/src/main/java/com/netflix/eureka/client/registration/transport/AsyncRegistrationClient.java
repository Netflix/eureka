package com.netflix.eureka.client.registration.transport;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka.client.registration.RegistrationClient;
import com.netflix.eureka.protocol.Heartbeat;
import com.netflix.eureka.protocol.registration.Register;
import com.netflix.eureka.protocol.registration.Unregister;
import com.netflix.eureka.protocol.registration.Update;
import com.netflix.eureka.transport.Acknowledgement;
import com.netflix.eureka.transport.MessageBroker;
import com.netflix.eureka.transport.UserContent;
import com.netflix.eureka.transport.utils.HeartBeatHandler;
import com.netflix.eureka.transport.utils.HeartBeatHandler.HeartbeatClient;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class AsyncRegistrationClient implements RegistrationClient {
    private final MessageBroker messageBroker;
    private final HeartbeatClient<Heartbeat> heartbeatClient;

    public AsyncRegistrationClient(MessageBroker messageBroker, long heatbeatInterval, TimeUnit heartbeatUnit) {
        this.messageBroker = messageBroker;
        heartbeatClient = new HeartBeatHandler.HeartbeatClient<Heartbeat>(messageBroker, heatbeatInterval, heartbeatUnit) {
            @Override
            protected Heartbeat heartbeatMessage() {
                return Heartbeat.HEART_BEAT;
            }
        };
    }

    @Override
    public Observable<Void> register(Register registryInfo) {
        return executeCommand(new UserContent(registryInfo));
    }

    @Override
    public Observable<Void> update(Update update) {
        return executeCommand(new UserContent(update));
    }

    @Override
    public Observable<Void> unregister() {
        return executeCommand(new UserContent(new Unregister()));
    }

    @Override
    public void shutdown() {
        heartbeatClient.shutdown();
        messageBroker.shutdown();
    }

    @Override
    public Observable<Void> lifecycleObservable() {
        return heartbeatClient.connectionStatus();
    }

    private Observable<Void> executeCommand(UserContent command) {
        Observable<Acknowledgement> ack = messageBroker.submitWithAck(command);
        return ack.map(new Func1<Acknowledgement, Void>() {
            @Override
            public Void call(Acknowledgement acknowledgement) {
                return null;
            }
        });
    }
}
