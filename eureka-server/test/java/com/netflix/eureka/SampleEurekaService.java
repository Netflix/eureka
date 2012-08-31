/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;

/**
 * Sample Eureka service that registers with Eureka to receive and process
 * requests.
 * 
 * <p>
 * This example just receives one request and exits once it receives the request
 * after processing it.
 * </p>
 * 
 * @author Karthik Ranganathan
 * 
 */
public class SampleEurekaService {
    private static final DynamicPropertyFactory configInstance = com.netflix.config.DynamicPropertyFactory
    .getInstance();

    private static final Logger logger = LoggerFactory
    .getLogger(SampleEurekaService.class);

    public void registerWithEureka() {
        // Register with Eureka
        DiscoveryManager.getInstance().initComponent(
                new MyDataCenterInstanceConfig(),
                new DefaultEurekaClientConfig());
        ApplicationInfoManager.getInstance().setInstanceStatus(
                InstanceStatus.UP);
        String vipAddress = configInstance.getStringProperty(
                "eureka.vipAddress", "sampleservice.mydomain.net").get();
        InstanceInfo nextServerInfo = null;
        while (nextServerInfo == null) {
            try {
                nextServerInfo = DiscoveryManager.getInstance()
                .getDiscoveryClient()
                .getNextServerFromEureka(vipAddress, false);
            } catch (Throwable e) {
                System.out
                .println("Waiting for service to register with eureka..");

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e1) {
                    // TODO Auto-generated catch block
                    e1.printStackTrace();
                }

            }
        }
        System.out.println("Service started and ready to process requests..");

        try {
            ServerSocket serverSocket = new ServerSocket(configInstance
                    .getIntProperty("eureka.port", 8010).get());
            final Socket s = serverSocket.accept();
            System.out
            .println("Client got connected..Processing request from the client");
            processRequest(s);

        } catch (IOException e) {
            e.printStackTrace();
        }
        this.unRegisterWithEureka();
        System.out.println("Shutting down server.Demo over.");

    }

    public void unRegisterWithEureka() {
        // Un register from eureka.
        DiscoveryManager.getInstance().shutdownComponent();
    }

    private void processRequest(final Socket s) {
        try {
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    s.getInputStream()));
            String line = rd.readLine();
            if (line != null) {
                System.out.println("Received the request from the client.");
            }
            PrintStream out = new PrintStream(s.getOutputStream());
            System.out.println("Sending the response to the client...");

            out.println("Reponse at " + new Date());

        } catch (Throwable e) {
            System.err.println("Error processing requests");
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

    }

    public static void main(String args[]) {
        SampleEurekaService sampleEurekaService = new SampleEurekaService();
        sampleEurekaService.registerWithEureka();
    }

}
