/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka2.testkit.compatibility.transport;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.InterestModel;
import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.interest.Interest;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.spi.channel.*;
import com.netflix.eureka2.spi.model.ClientHello;
import com.netflix.eureka2.spi.model.ServerHello;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;
import rx.subjects.ReplaySubject;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 */
public abstract class EurekaTransportCompatibilityTestSuite {

    private ClientHello clientHello;
    private ServerHello serverHello;
    private InstanceInfo instance;

    private InstanceModel instanceModel;
    private InterestModel interestModel;
    private TransportModel transportModel;

    private final RegistrationHandler registrationAcceptor = new TestableRegistrationAcceptor();

    private final InterestHandler interestAcceptor = new TestableInterestTransportHandler();
    private Subscription serverSubscription;
    private Server eurekaServer;

    @Before
    public void setup() {
        instanceModel = InstanceModel.getDefaultModel();
        interestModel = InterestModel.getDefaultModel();
        transportModel = TransportModel.getDefaultModel();

        clientHello = transportModel.newClientHello(instanceModel.createSource(Source.Origin.LOCAL, "testClient", 1));
        Source serverSource = instanceModel.createSource(Source.Origin.LOCAL, "testServer", 1);
        serverHello = transportModel.newServerHello(serverSource);
        instance = SampleInstanceInfo.WebServer.build();

        BlockingQueue<EurekaServerTransportFactory.ServerContext> serverContextQueue = new LinkedBlockingQueue<>();

        ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory = new ChannelPipelineFactory<InstanceInfo, InstanceInfo>() {
            @Override
            public Observable<ChannelPipeline<InstanceInfo, InstanceInfo>> createPipeline() {
                return Observable.just(new ChannelPipeline<>("registration", registrationAcceptor));
            }
        };
        ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> interestPipelineFactory = new ChannelPipelineFactory<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>() {
            @Override
            public Observable<ChannelPipeline<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>>> createPipeline() {
                return Observable.just(new ChannelPipeline<>("interest", interestAcceptor));
            }
        };

        serverSubscription = newServerTransportFactory().connect(0, serverSource, registrationPipelineFactory, interestPipelineFactory, null)
                .doOnNext(context -> serverContextQueue.add(context))
                .doOnError(e -> e.printStackTrace())
                .subscribe();
        EurekaServerTransportFactory.ServerContext serverContext = serverContextQueue.poll();
        eurekaServer = new Server("localhost", serverContext.getPort());
    }

    @After
    public void tearDown() {
        if (serverSubscription != null) {
            serverSubscription.unsubscribe();
        }
    }

    protected abstract EurekaClientTransportFactory newClientTransportFactory();

    protected abstract EurekaServerTransportFactory newServerTransportFactory();

    @Test(timeout = 30000)
    public void testRegistrationHello() throws InterruptedException {
        RegistrationHandler clientTransport = newClientTransportFactory().newRegistrationClientTransport(eurekaServer);

        ReplaySubject<ChannelNotification<InstanceInfo>> registrations = ReplaySubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        clientTransport.handle(registrations).subscribe(testSubscriber);

        // Send hello
        registrations.onNext(ChannelNotification.newHello(clientHello));
        ChannelNotification<InstanceInfo> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getKind(), is(equalTo(ChannelNotification.Kind.Hello)));
        assertThat(helloReply.getHello(), is(equalTo(serverHello)));
    }

    @Test(timeout = 30000)
    public void testRegistrationHeartbeat() throws InterruptedException {
        RegistrationHandler clientTransport = newClientTransportFactory().newRegistrationClientTransport(eurekaServer);

        ReplaySubject<ChannelNotification<InstanceInfo>> registrations = ReplaySubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        clientTransport.handle(registrations).subscribe(testSubscriber);

        // Send heartbeat
        registrations.onNext(ChannelNotification.newHeartbeat());
        ChannelNotification<InstanceInfo> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getKind(), is(equalTo(ChannelNotification.Kind.Heartbeat)));
    }

    @Test(timeout = 30000)
    public void testRegistrationConnection() throws InterruptedException {
        RegistrationHandler clientTransport = newClientTransportFactory().newRegistrationClientTransport(eurekaServer);

        ReplaySubject<ChannelNotification<InstanceInfo>> registrations = ReplaySubject.create();
        ExtTestSubscriber<ChannelNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

        clientTransport.handle(registrations).subscribe(testSubscriber);

        // Send data
        registrations.onNext(ChannelNotification.newData(instance));
        ChannelNotification<InstanceInfo> confirmation = testSubscriber.takeNextOrWait();
        assertThat(confirmation.getKind(), is(equalTo(ChannelNotification.Kind.Data)));
    }

    @Test(timeout = 30000)
    public void testInterestHello() throws InterruptedException {
        InterestHandler clientTransport = newClientTransportFactory().newInterestTransport(eurekaServer);
        PublishSubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = PublishSubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        interestNotifications.onNext(ChannelNotification.newHello(clientHello));

        ChannelNotification<ChangeNotification<InstanceInfo>> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getHello(), is(equalTo(serverHello)));
    }

    @Test(timeout = 30000)
    public void testInterestHeartbeat() throws InterruptedException {
        InterestHandler clientTransport = newClientTransportFactory().newInterestTransport(eurekaServer);
        PublishSubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = PublishSubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        interestNotifications.onNext(ChannelNotification.newHeartbeat());

        ChannelNotification<ChangeNotification<InstanceInfo>> helloReply = testSubscriber.takeNextOrWait();
        assertThat(helloReply.getKind(), is(equalTo(ChannelNotification.Kind.Heartbeat)));
    }

    @Test(timeout = 30000)
    public void testInterestConnection() throws InterruptedException {
        InterestHandler clientTransport = newClientTransportFactory().newInterestTransport(eurekaServer);
        ReplaySubject<ChannelNotification<Interest<InstanceInfo>>> interestNotifications = ReplaySubject.create();

        ExtTestSubscriber<ChannelNotification<ChangeNotification<InstanceInfo>>> testSubscriber = new ExtTestSubscriber<>();
        clientTransport.handle(interestNotifications).subscribe(testSubscriber);

        interestNotifications.onNext(ChannelNotification.newHello(clientHello));
        interestNotifications.onNext(ChannelNotification.newData(interestModel.newFullRegistryInterest()));

        ChannelNotification<ChangeNotification<InstanceInfo>> notification = testSubscriber.takeNextOrWait();
        assertThat(notification.getKind(), is(equalTo(ChannelNotification.Kind.Hello)));

        ChannelNotification<ChangeNotification<InstanceInfo>> notification2 = testSubscriber.takeNextOrWait();
        assertThat(notification2.getData().getData(), is(equalTo(instance)));
    }

    class TestableRegistrationAcceptor implements RegistrationHandler {
        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {

        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
            return Observable.create(subscriber -> {
                registrationUpdates.subscribe(
                        next -> {
                            if (next.getKind() == ChannelNotification.Kind.Hello) {
                                subscriber.onNext(ChannelNotification.<ServerHello, InstanceInfo>newHello(serverHello));
                            } else {
                                subscriber.onNext(next);
                            }
                        },
                        e -> e.printStackTrace()
                );
            });
        }
    }

    class TestableInterestTransportHandler implements InterestHandler {
        @Override
        public void init(ChannelContext<Interest<InstanceInfo>, ChangeNotification<InstanceInfo>> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<ChangeNotification<InstanceInfo>>> handle(Observable<ChannelNotification<Interest<InstanceInfo>>> interests) {
            return Observable.create(subscriber -> {
                ChangeNotification<InstanceInfo> addNotification = new ChangeNotification<InstanceInfo>(ChangeNotification.Kind.Add, instance);
                interests
                        .doOnNext(interest -> {
                            switch (interest.getKind()) {
                                case Hello:
                                    ChannelNotification<ChangeNotification<InstanceInfo>> serverHelloNotification = ChannelNotification.newHello(serverHello);
                                    subscriber.onNext(serverHelloNotification);
                                    break;
                                case Heartbeat:
                                    subscriber.onNext(ChannelNotification.<ChangeNotification<InstanceInfo>>newHeartbeat());
                                    break;
                                case Data:
                                    subscriber.onNext(ChannelNotification.newData(addNotification));
                            }
                        })
                        .doOnError(e -> e.printStackTrace())
                        .subscribe();
            });
        }
    }
}
