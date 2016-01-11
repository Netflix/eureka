/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.eureka2.performance.transport;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.Server;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.channel.*;
import com.netflix.eureka2.spi.transport.EurekaClientTransportFactory;
import com.netflix.eureka2.spi.transport.EurekaServerTransportFactory;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.transport.client.StdEurekaClientTransportFactory;
import com.netflix.eureka2.transport.server.StdEurekaServerTransportFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.subjects.PublishSubject;

/**
 */
public class TransportPerf {

    private static final Logger logger = LoggerFactory.getLogger(TransportPerf.class);

    private static final long INTERVAL = 5000;

    private final Configuration config;
    private final EurekaClientTransportFactory clientTransportFactory;
    private final EurekaServerTransportFactory serverTransportFactory;

    private int targetPort;
    private Subscription serverSubscription;

    protected TransportPerf(String[] args,
                            EurekaClientTransportFactory clientTransportFactory,
                            EurekaServerTransportFactory serverTransportFactory) {
        this.clientTransportFactory = clientTransportFactory;
        this.serverTransportFactory = serverTransportFactory;
        this.config = Configuration.parseCommandLineArgs(args);
    }

    public boolean isRunnable() {
        return config != null;
    }

    public void start() throws InterruptedException {
        if (!isRunnable()) {
            return;
        }

        bootstrapServer();

        switch (config.getProtocolType()) {
            case Registration:
                startRegistration();
                break;
        }

        logger.info("Performance test started...");
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            // IGNORE
        }
        logger.info("Exiting");
    }

    private void bootstrapServer() throws InterruptedException {
        ChannelPipelineFactory<InstanceInfo, InstanceInfo> registrationPipelineFactory = new ChannelPipelineFactory<InstanceInfo, InstanceInfo>() {
            @Override
            public Observable<ChannelPipeline<InstanceInfo, InstanceInfo>> createPipeline() {
                return Observable.just(new ChannelPipeline<>("registration", new ServerRegistrationHandler()));
            }
        };

        BlockingQueue<EurekaServerTransportFactory.ServerContext> serverContextQueue = new LinkedBlockingQueue<>();

        this.serverSubscription = serverTransportFactory.connect(0, registrationPipelineFactory, null, null)
                .doOnNext(context -> serverContextQueue.add(context))
                .doOnError(e -> e.printStackTrace())
                .subscribe();
        EurekaServerTransportFactory.ServerContext serverContext = serverContextQueue.poll(30, TimeUnit.SECONDS);

        this.targetPort = serverContext.getPort();
    }

    private void startRegistration() throws InterruptedException {
        RegistrationHandler clientHandler = clientTransportFactory.newRegistrationClientTransport(new Server("localhost", targetPort));

        Semaphore ackSemaphor = new Semaphore(0);

        PublishSubject<ChannelNotification<InstanceInfo>> updates = PublishSubject.create();
        clientHandler.handle(updates).subscribe(
                next -> ackSemaphor.release(),
                e -> logger.error("Registration transport error", e),
                () -> logger.info("Registration transport onCompleted")
        );
        while (!updates.hasObservers()) { // Otherwise we may miss first updates
            Thread.sleep(1);
        }

        InstanceInfo instance = SampleInstanceInfo.Backend.build();
        long endTime = System.currentTimeMillis() + INTERVAL;
        int count = 0;
        while (true) {
            updates.onNext(ChannelNotification.newData(instance));
            ackSemaphor.acquire();

            long now = System.currentTimeMillis();
            if (now > endTime) {
                System.out.println("Rate: " + count * 1000 / INTERVAL + "[req/sec]");
                endTime = now + INTERVAL;
                count = 1;
            } else {
                count++;
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new TransportPerf(args, new StdEurekaClientTransportFactory(), new StdEurekaServerTransportFactory()).start();
    }

    private static class ServerRegistrationHandler implements RegistrationHandler {
        @Override
        public void init(ChannelContext<InstanceInfo, InstanceInfo> channelContext) {
        }

        @Override
        public Observable<ChannelNotification<InstanceInfo>> handle(Observable<ChannelNotification<InstanceInfo>> registrationUpdates) {
            return registrationUpdates.flatMap(update -> {
                if (update.getKind() == ChannelNotification.Kind.Data) {
                    return Observable.just(ChannelNotification.newData(update.getData()));
                }
                return Observable.empty();
            });
        }
    }
}
