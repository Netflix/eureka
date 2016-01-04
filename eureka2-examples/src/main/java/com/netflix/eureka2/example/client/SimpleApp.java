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

package com.netflix.eureka2.example.client;

import com.netflix.eureka2.client.EurekaInterestClient;
import com.netflix.eureka2.client.EurekaRegistrationClient;
import com.netflix.eureka2.client.Eurekas;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.datacenter.LocalDataCenterInfo;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfo.Status;
import com.netflix.eureka2.model.notification.ChangeNotification;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.BehaviorSubject;

import static com.netflix.eureka2.client.resolver.ServerResolvers.fromDnsName;
import static com.netflix.eureka2.client.resolver.ServerResolvers.fromEureka;
import static com.netflix.eureka2.model.interest.Interests.forApplications;
import static com.netflix.eureka2.model.interest.Interests.forVips;

/**
 * This example demonstrates how to register an application using {@link EurekaRegistrationClient}
 * and how to access registry data using {@link EurekaInterestClient}.
 *
 * @author Tomasz Bak
 */
public final class SimpleApp {

    public static final InstanceInfo SERVICE_A = InstanceModel.getDefaultModel().newInstanceInfo()
            .withId("id_serviceA")
            .withApp("ServiceA")
            .withAppGroup("ServiceA_1")
            .withStatus(Status.UP)
            .withDataCenterInfo(LocalDataCenterInfo.fromSystemData())
            .build();

    private final String writeServerDns;
    private final int writeRegistrationPort;
    private final int serverPort;
    private final String readServerVip;

    public SimpleApp(String writeServerDns, int writeRegistrationPort, int serverPort, String readServerVip) {
        this.writeServerDns = writeServerDns;
        this.writeRegistrationPort = writeRegistrationPort;
        this.serverPort = serverPort;
        this.readServerVip = readServerVip;
    }

    public void run() throws InterruptedException {

        EurekaRegistrationClient registrationClient = Eurekas.newRegistrationClientBuilder()
                .withServerResolver(fromDnsName(writeServerDns).withPort(writeRegistrationPort))
                .build();

        ServerResolver interestClientResolver =
                fromEureka(
                        fromDnsName(writeServerDns).withPort(serverPort)
                ).forInterest(forVips(readServerVip));

        EurekaInterestClient interestClient = Eurekas.newInterestClientBuilder()
                .withServerResolver(interestClientResolver)
                .build();

        interestClient.forInterest(forApplications("WriteServer", "ReadServer", "ServiceA")).subscribe(
                new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("Change notification stream closed");
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("Error in the notification channel: " + e);
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> changeNotification) {
                        System.out.println("Received notification: " + changeNotification);
                    }
                });

        BehaviorSubject<InstanceInfo> infoSubject = BehaviorSubject.create();
        Subscription subscription = registrationClient.register(infoSubject).subscribe();

        // Register client 1
        System.out.println("Registering SERVICE_A with Eureka...");
        infoSubject.onNext(SERVICE_A);
        Thread.sleep(1000);

        // Modify client 1 status
        System.out.println("Updating service status to DOWN...");
        InstanceInfo updatedInfo = InstanceModel.getDefaultModel().newInstanceInfo().withInstanceInfo(SERVICE_A).withStatus(Status.DOWN).build();
        infoSubject.onNext(updatedInfo);
        Thread.sleep(1000);

        // Unregister client 1
        System.out.println("Unregistering SERVICE_A from Eureka...");
        subscription.unsubscribe();
        Thread.sleep(1000);

        Thread.sleep(5000);

        // Terminate both clients.
        System.out.println("Shutting down clients");
        registrationClient.shutdown();
        interestClient.shutdown();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Local service info: " + SERVICE_A);
        SimpleApp simpleApp = new SimpleApp("localhost", 13100, 13101, "eurekaReadServerVip");
        simpleApp.run();
    }
}
