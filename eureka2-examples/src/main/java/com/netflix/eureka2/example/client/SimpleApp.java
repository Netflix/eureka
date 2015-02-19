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

import com.netflix.eureka2.client.Eureka;
import com.netflix.eureka2.client.EurekaClient;
import com.netflix.eureka2.client.resolver.ServerResolver;
import com.netflix.eureka2.client.resolver.ServerResolvers;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.datacenter.BasicDataCenterInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.registry.instance.InstanceInfo.Builder;
import com.netflix.eureka2.registry.instance.InstanceInfo.Status;
import rx.Subscriber;
import rx.Subscription;
import rx.subjects.BehaviorSubject;

/**
 * This example demonstrates how to register an application using {@link EurekaClient},
 * and how to access registry data.
 *
 * @author Tomasz Bak
 */
public final class SimpleApp {

    public static final InstanceInfo SERVICE_A = new Builder()
            .withId("id_serviceA")
            .withApp("ServiceA")
            .withAppGroup("ServiceA_1")
            .withStatus(Status.UP)
            .withDataCenterInfo(BasicDataCenterInfo.fromSystemData())
            .build();


    private final ServerResolver writeClusterDiscoveryResolver;
    private final ServerResolver writeClusterRegistrationResolver;
    private final String readServerVip;

    public SimpleApp(String eurekaWriteServer, int registrationPort, int discoveryPort, String readServerVip) {
        this.writeClusterDiscoveryResolver = ServerResolvers.just(eurekaWriteServer, discoveryPort);
        this.writeClusterRegistrationResolver = ServerResolvers.just(eurekaWriteServer, registrationPort);
        this.readServerVip = readServerVip;
    }

    public void run() throws InterruptedException {

        EurekaClient client = Eureka.newClientBuilder(
                writeClusterDiscoveryResolver,
                writeClusterRegistrationResolver,
                readServerVip
        ).build();

        client.forInterest(Interests.forApplications("WriteServer", "ReadServer", "ServiceA")).subscribe(
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
        Subscription subscription = client.register(infoSubject).subscribe();

        // Register client 1
        System.out.println("Registering SERVICE_A with Eureka...");
        infoSubject.onNext(SERVICE_A);
        Thread.sleep(1000);

        // Modify client 1 status
        System.out.println("Updating service status to DOWN...");
        InstanceInfo updatedInfo = new Builder().withInstanceInfo(SERVICE_A).withStatus(Status.DOWN).build();
        infoSubject.onNext(updatedInfo);
        Thread.sleep(1000);

        // Unregister client 1
        System.out.println("Unregistering SERVICE_A from Eureka...");
        subscription.unsubscribe();
        Thread.sleep(1000);

        Thread.sleep(5000);

        // Terminate both clients.
        System.out.println("Shutting down clients");
        client.shutdown();
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Local service info: " + SERVICE_A);
        SimpleApp simpleApp = new SimpleApp("localhost", 13100, 13101, "eurekaReadServerVip");
        simpleApp.run();
    }
}
