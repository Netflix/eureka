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

package com.netflix.eureka.example.simple;

import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.EurekaClients;
import com.netflix.eureka.client.transport.TransportClients;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.registry.BasicDataCenterInfo;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfo.Builder;
import com.netflix.eureka.registry.InstanceInfo.Status;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import rx.Subscriber;

/**
 * This example demonstrates how to register an application using {@link EurekaClient},
 * and how to access registry data.
 *
 * @author Tomasz Bak
 */
public class SimpleApp {

    public static final InstanceInfo SERVICE_A = new Builder()
            .withId("id_serviceA")
            .withApp("ServiceA")
            .withAppGroup("ServiceA_1")
            .withStatus(Status.UP)
            .withInstanceLocation(BasicDataCenterInfo.fromSystemData())
            .build();

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Local service info: " + SERVICE_A);

        // TODO: servers now use by default JSON codec. Remove it, once they are switch to Avro.
        TransportClients.setDefaultCodec(Codec.Json);

        EurekaClient client1 = EurekaClients.forRegistration("localhost:7200").toBlocking().first();
        EurekaClient client2 = EurekaClients.forDiscovery("localhost:7300").toBlocking().first();

        // Client 2 collects notifications about ServiceA
        client2.forInterest(Interests.forFullRegistry()).subscribe(
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

        // Register client 1
        System.out.println("Registering SERVICE_A with Eureka...");
        client1.register(SERVICE_A).toBlocking().singleOrDefault(null);
        Thread.sleep(1000);

        // Modify client 1 status
        System.out.println("Updating service status to DOWN...");
        InstanceInfo updatedInfo = new Builder().withInstanceInfo(SERVICE_A).withStatus(Status.DOWN).build();
        client1.update(updatedInfo).toBlocking().singleOrDefault(null);
        Thread.sleep(1000);

        // Unregister client 1
        System.out.println("Unregistering SERVICE_A from Eureka...");
        client1.unregister(updatedInfo).toBlocking().singleOrDefault(null);
        Thread.sleep(1000);

        // Terminate both clients.
        System.out.println("Shutting down clients");
        client1.close();
        client2.close();
    }
}
