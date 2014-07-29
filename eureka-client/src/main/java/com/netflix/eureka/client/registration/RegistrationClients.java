package com.netflix.eureka.client.registration;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka.EurekaEndpoints;
import com.netflix.eureka.client.registration.transport.AsyncRegistrationClient;
import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public class RegistrationClients {

    Observable<RegistrationClient> tcpRegistrationClient(SocketAddress address) {
        // FIXME It is just a stub. We need to be smarter here.
        InetSocketAddress ipAddress = (InetSocketAddress) address;
        Observable<MessageBroker> messageBrokerObservable = EurekaEndpoints.tcpRegistrationClient(ipAddress.getHostName(), ipAddress.getPort());
        return messageBrokerObservable.map(new Func1<MessageBroker, RegistrationClient>() {
            @Override
            public RegistrationClient call(MessageBroker messageBroker) {
                return new AsyncRegistrationClient(messageBroker, 0, TimeUnit.MILLISECONDS);
            }
        });
    }

    RegistrationClient websocketRegistrationClient(SocketAddress address) {
        return null;
    }

    RegistrationClient httpRegistrationClient(SocketAddress address) {
        return null;
    }
}
