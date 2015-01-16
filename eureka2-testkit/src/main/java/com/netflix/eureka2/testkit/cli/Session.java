/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.testkit.cli;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.Sets;
import rx.Subscriber;

/**
 * Represents single registration/interest connection.
 *
 * @author Tomasz Bak
 */
public class Session {

    public enum Status {NotStarted, Initiated, Streaming, Complete, Failed;}

    public enum Mode {Read, Write, ReadWrite}

    private static final AtomicInteger sessionIds = new AtomicInteger(0);

    private final Context context;

    private final int sessionId = sessionIds.incrementAndGet();

    private Mode mode;

    private Status registrationStatus = Status.NotStarted;

    private volatile InstanceInfo lastInstanceInfo;
    private EurekaClient eurekaClient;

    private final AtomicInteger streamIds = new AtomicInteger();
    private final Map<String, InterestSubscriber> subscriptions = new HashMap<>();

    public Session(Context context) {
        this.context = context;
    }

    public Status getRegistrationStatus() {
        return registrationStatus;
    }

    public boolean expectedRegistrationStatus(Status... expected) {
        if (Sets.asSet(expected).contains(registrationStatus)) {
            return true;
        }
        switch (registrationStatus) {
            case NotStarted:
                System.out.println("ERROR: Registration not started yet.");
                break;
            case Initiated:
                System.out.println("ERROR: Registration already in progress.");
                break;
            case Complete:
                System.out.println("ERROR: Registration already done.");
                break;
            case Failed:
                System.out.println("ERROR: Previous registration failed.");
                break;
        }
        return false;
    }

    public int getSessionId() {
        return sessionId;
    }

    public InstanceInfo getInstanceInfo() {
        return lastInstanceInfo;
    }

    public void connectToRegister(String host, int port) {
        eurekaClient = Eureka.newClientBuilder(ServerResolvers.just(host, port))
                .withCodec(context.getCodec())
                .build();
        mode = Mode.Write;
    }

    public void connectToRead(String host, int port) {
        eurekaClient = Eureka.newClientBuilder(ServerResolvers.just(host, port))
                .withCodec(context.getCodec())
                .build();
        mode = Mode.Read;
    }

    public void connectToCluster(String host, int registrationPort, int discoveryPort, String readClusterVip) {
        eurekaClient = Eureka.newClientBuilder(
                ServerResolvers.fromWriteServer(ServerResolvers.just(host, discoveryPort), readClusterVip),
                ServerResolvers.just(host, registrationPort)
        ).withCodec(context.getCodec()).build();
        mode = Mode.ReadWrite;
    }

    public boolean isConnected() {
        if (eurekaClient == null) {
            System.out.println("ERROR: connect first to Eureka server");
            return false;
        }
        return true;
    }

    public void register(final InstanceInfo instanceInfo) {
        if(mode == Mode.Read) {
            System.err.println("ERROR: subscription-only session");
            return;
        }

        registrationStatus = Status.Initiated;
        eurekaClient.register(instanceInfo)
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("Successfully registered with Eureka server");
                        lastInstanceInfo = instanceInfo;
                        registrationStatus = Status.Complete;
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("ERROR: Registration failed.");
                        e.printStackTrace();
                        registrationStatus = Status.Failed;
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No op
                    }
                });
    }

    public void update(final InstanceInfo newInfo) {
        if(mode == Mode.Read) {
            System.err.println("ERROR: subscription-only session");
            return;
        }
        eurekaClient.register(newInfo).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                System.out.println("Successfully updated registry information.");
                lastInstanceInfo = newInfo;
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("ERROR: Registration update failed.");
                e.printStackTrace();
                registrationStatus = Status.Failed;
            }

            @Override
            public void onNext(Void aVoid) {
                // No op
            }
        });

    }

    public void unregister() {
        if(mode == Mode.Read) {
            System.err.println("ERROR: subscription-only session");
            return;
        }
        eurekaClient.unregister(lastInstanceInfo)
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("Successfuly unregistered with Eureka server");
                        registrationStatus = Status.NotStarted;
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("ERROR: Unregistration failed.");
                        e.printStackTrace();
                        registrationStatus = Status.Failed;
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No op
                    }
                });
    }

    public void forInterest(Interest<InstanceInfo> interest) {
        if(mode == Mode.Write) {
            System.err.println("ERROR: registration-only session");
            return;
        }
        String id = sessionId + "#" + streamIds.incrementAndGet();
        InterestSubscriber subscriber = new InterestSubscriber(interest, id);
        subscriptions.put(id, subscriber);
        eurekaClient.forInterest(interest).subscribe(subscriber);

        System.out.println("Stream_" + id + ": Subscribing to Interest: " + interest);
    }

    public void close() {
        if (eurekaClient != null) {
            System.out.println("Closing session " + sessionId);
            eurekaClient.close();
            eurekaClient = null;
        }
    }

    public void printStatus() {
        System.out.println("Session " + sessionId);
        if (eurekaClient == null) {
            System.out.println("Connection status: disconnected");
        } else {
            System.out.println("Connection status: connected in mode " + mode);
            switch (registrationStatus) {
                case NotStarted:
                    System.out.println("Registration status: unregistered");
                    break;
                case Initiated:
                    System.out.println("Registration status: Initiated but not completed.");
                    break;
                case Complete:
                    System.out.println("Registration status: registered");
                    break;
                case Failed:
                    System.out.println("Registration status: failed");
                    break;
            }
            System.out.println("Number of subscriptions: " + subscriptions.size());
            for (Map.Entry<String, InterestSubscriber> entry : subscriptions.entrySet()) {
                System.out.println("Stream_" + entry.getKey() + " -> " + entry.getValue().interest);
            }
        }
        System.out.println();
    }

    static class InterestSubscriber extends Subscriber<ChangeNotification<InstanceInfo>> {

        private final Interest<InstanceInfo> interest;
        private final String id;
        private Status subscriptionStatus = Status.Initiated;

        InterestSubscriber(final Interest<InstanceInfo> interest, final String id) {
            this.interest = interest;
            this.id = id;
        }

        public Status getSubscriptionStatus() {
            return subscriptionStatus;
        }

        @Override
        public void onCompleted() {
            subscriptionStatus = Status.Complete;
            System.out.println("Stream_" + id + ": Interest " + interest + " COMPLETE");
        }

        @Override
        public void onError(Throwable e) {
            subscriptionStatus = Status.Failed;
            System.out.println("Stream_" + id + ": Interest " + interest + " ERROR: " + e);
        }

        @Override
        public void onNext(ChangeNotification<InstanceInfo> notification) {
            subscriptionStatus = Status.Streaming;
            System.out.println("Stream_" + id + ": Interest " + interest + " NEXT: " + notification);
        }
    }

}
