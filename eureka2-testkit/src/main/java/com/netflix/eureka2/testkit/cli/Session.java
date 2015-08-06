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

import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.ExtCollections;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.BehaviorSubject;

/**
 * Represents single registration/interest connection.
 *
 * @author Tomasz Bak
 */
public class Session {

    public enum Status {NotStarted, Initiated, Streaming, Complete, Failed;}

    private static final AtomicInteger sessionIds = new AtomicInteger(0);

    private final int sessionId = sessionIds.incrementAndGet();

    private volatile Subscription registrationSubscription;
    private Status registrationStatus = Status.NotStarted;
    private final BehaviorSubject<InstanceInfo> infoSubject = BehaviorSubject.create();

    private volatile InstanceInfo lastInstanceInfo;
    private final SessionDescriptor descriptor;
    private EurekaRegistrationClient registrationClient;
    private EurekaInterestClient interestClient;

    private final AtomicInteger streamIds = new AtomicInteger();
    private final Map<String, InterestSubscriber> subscriptions = new HashMap<>();
    private final Map<String, Subscription> pendingSubscriptions = new HashMap<>();

    public Session(SessionDescriptor descriptor,
                   EurekaRegistrationClient registrationClient,
                   EurekaInterestClient interestClient) {
        this.descriptor = descriptor;
        this.registrationClient = registrationClient;
        this.interestClient = interestClient;
    }

    public EurekaRegistrationClient getRegistrationClient() {
        return registrationClient;
    }

    public EurekaInterestClient getInterestClient() {
        return interestClient;
    }

    public String getPrompt() {
        return "session";
    }

    public Status getRegistrationStatus() {
        return registrationStatus;
    }

    public boolean expectedRegistrationStatus(Status... expected) {
        if (ExtCollections.asSet(expected).contains(registrationStatus)) {
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

    public InstanceInfo getInstanceInfo() {
        return lastInstanceInfo;
    }

    public void register(final InstanceInfo instanceInfo) {
        if (descriptor.isReadOnly()) {
            System.err.println("ERROR: subscription-only session");
            return;
        }

        registrationStatus = Status.Initiated;
        RegistrationObservable registrationRequest = registrationClient.register(infoSubject);
        registrationRequest.initialRegistrationResult().subscribe(new Subscriber<Void>() {
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
                // no-op
            }
        });

        registrationSubscription = registrationRequest.subscribe();
        infoSubject.onNext(instanceInfo);
    }

    public void update(final InstanceInfo newInfo) {
        if (descriptor.isReadOnly()) {
            System.err.println("ERROR: subscription-only session");
            return;
        }
        lastInstanceInfo = newInfo;
        infoSubject.onNext(newInfo);
    }

    public void unregister() {
        if (descriptor.isReadOnly()) {
            System.err.println("ERROR: subscription-only session");
            return;
        }

        if (registrationSubscription != null) {
            registrationSubscription.unsubscribe();
        }
        registrationStatus = Status.NotStarted;
    }

    public String nextSubscriptionId() {
        return sessionId + "#" + streamIds.incrementAndGet();
    }

    public void addSubscription(String id, Subscription subscription) {
        pendingSubscriptions.put(id, subscription);
    }

    public void forInterest(Interest<InstanceInfo> interest) {
        String id = nextSubscriptionId();
        InterestSubscriber subscriber = new InterestSubscriber(interest, id);
        subscriptions.put(id, subscriber);
        interestClient.forInterest(interest).subscribe(subscriber);

        System.out.println("Stream_" + id + ": Subscribing to Interest: " + interest);
    }

    public boolean disconnect(String sessionId) {
        if (subscriptions.containsKey(sessionId)) {
            subscriptions.remove(sessionId).unsubscribe();
            return true;
        }
        if (pendingSubscriptions.containsKey(sessionId)) {
            pendingSubscriptions.remove(sessionId).unsubscribe();
            return true;
        }
        return false;
    }

    public void close() {
        System.out.println("Closing session " + sessionId);
        if (registrationClient != null) {
            registrationClient.shutdown();
            registrationClient = null;
            System.out.println("Shutdown registration client");
        }
        if (interestClient != null) {
            interestClient.shutdown();
            interestClient = null;
            System.out.println("Shutdown interest client");
        }
    }

    public void printFormatted(PrintStream out, int indentSize) {
        char[] indent = new char[indentSize];
        Arrays.fill(indent, ' ');

        out.print(indent);
        out.println("Session id: " + sessionId);
        out.print(indent);
        descriptor.printFormatted(out, indentSize);
        switch (registrationStatus) {
            case NotStarted:
                out.print(indent);
                out.print(indent);
                out.println("Registration status: unregistered");
                break;
            case Initiated:
                out.print(indent);
                out.print(indent);
                out.println("Registration status: Initiated but not completed.");
                break;
            case Complete:
                out.print(indent);
                out.print(indent);
                out.println("Registration status: registered");
                break;
            case Failed:
                out.print(indent);
                out.print(indent);
                out.println("Registration status: failed");
                break;
        }
        out.print(indent);
        out.print(indent);
        out.println("Number of subscriptions: " + (subscriptions.size() + pendingSubscriptions.size()));
        for (Map.Entry<String, InterestSubscriber> entry : subscriptions.entrySet()) {
            out.print(indent);
            out.print(indent);
            out.print(indent);
            out.println(entry.getKey() + " -> " + entry.getValue().interest);
        }
        for (String id : pendingSubscriptions.keySet()) {
            out.print(indent);
            out.print(indent);
            out.print(indent);
            out.println(id + " -> query");
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
